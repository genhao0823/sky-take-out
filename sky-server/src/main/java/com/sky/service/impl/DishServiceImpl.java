package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class DishServiceImpl implements com.sky.service.DishService {

    @Autowired
    private DishMapper dishMapper;

    
    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 用于操作Redis缓存。
     * 当前业务中使用它缓存C端按分类查询出来的菜品列表。
     */
    @Autowired
    private RedisTemplate redisTemplate;

    //新增菜品和对应的口味
    @Override
    @Transactional
    public void saveWithFlavour(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        //向菜品表插入一条数据
        dishMapper.insert(dish);
//<!--    useGeneratedKeys="true" 获得insert语句插入时获得的主键值 产生的主键值会赋值给id-->
//    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
//        insert into dish (name, category_id, price, image, description, status, create_time, update_time)
//        values (#{name}, #{categoryId}, #{price}, #{image}, #{description}, #{status}, #{createTime}, #{updateTime})
//    </insert>
        //获取insert语句生成的主键值
        Long dishId = dish.getId();


        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!=null && flavors.size()>0){
            flavors.forEach(dishFlavor ->  {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }

        // 菜品数据发生变化后，删除C端菜品缓存，保证用户下次查询到最新数据。
        cleanCache("dish_*");
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        //PageHelper.startPage(当前页码, 每页记录数);
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page =dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 菜品批量删除
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除 是否存在起售中的菜品
        for (Long id : ids) {
            Dish dish  = dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){
                //起售中不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //是否被套餐关联？
        List<Long> setmealIds = setmealDishMapper.getDishIdBySetmealId(ids);//判断是否能在套餐中获取到id
        if(setmealIds!=null&&setmealIds.size()>0){
            //当前的菜品被套餐关联了 不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //删除菜品中的菜品数据
//        for (Long id : ids) {
//            dishMapper.deleteById(id);
//            //删除菜品关联的口味数据
//            dishFlavorMapper.deleteByDishId(id);
//        }

        //根据菜品id集合批量删除菜品数据
        dishMapper.deleteBatch(ids);

        //根据菜品id集合批量删除口味数据
        dishFlavorMapper.deleteByDishIds(ids);

        // 菜品数据发生变化后，删除C端菜品缓存，保证用户下次查询到最新数据。
        cleanCache("dish_*");
    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Override
    public DishVO getByWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        //根据id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装到vo
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 根据id修改菜品基本信息和对应的数据
     * @param dishDTO
     */
    @Transactional
    @Override
    public void update(DishDTO dishDTO) {
        //先修改菜品表基本信息
        Dish dish  = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        //先删除原有的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //再重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }

        // 菜品数据发生变化后，删除C端菜品缓存，保证用户下次查询到最新数据。
        cleanCache("dish_*");
    }


    /**
     * 根据分类id查询起售中的菜品和口味。
     *
     * 业务流程：
     * 1. 根据分类id拼接Redis缓存key，例如 dish_1。
     * 2. 先查询Redis，如果缓存中存在该分类菜品，直接返回，避免重复查询数据库。
     * 3. 如果Redis中不存在缓存，则构造Dish查询条件，只查询当前分类下起售中的菜品。
     * 4. 查询数据库中的菜品基本信息，再逐个查询菜品口味并封装成DishVO。
     * 5. 将数据库查询结果写入Redis，后续同分类查询可以直接命中缓存。
     *
     * @param categoryId 分类id
     * @return 当前分类下起售中的菜品和口味列表
     */
    @Override
    public List<DishVO> listWithFlavor(Long categoryId) {
        // 按分类缓存菜品列表，不同分类使用不同key，互不影响。
        String key = "dish_" + categoryId;

        // 先查Redis缓存；命中缓存时直接返回，不再访问数据库。
        List<DishVO> dishVOList = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (dishVOList != null) {
            return dishVOList;
        }

        // 缓存未命中时，构造查询条件：只查询当前分类下起售中的菜品。
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);

        List<Dish> dishList = dishMapper.list(dish);

        dishVOList = new ArrayList<>();

        // 菜品基本信息来自dish表，口味信息来自dish_flavor表，需要组装成前端需要的DishVO。
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        // 数据库查询完成后写入Redis，下次查询同分类菜品时可以直接从缓存返回。
        redisTemplate.opsForValue().set(key, dishVOList);

        return dishVOList;
    }

    /**
     * 清理菜品缓存。
     * 后台新增、修改、删除菜品后，原来的分类缓存可能已经不是最新数据，
     * 因此统一删除 dish_* 缓存，让下一次C端查询重新查数据库并回写Redis。
     *
     * @param pattern Redis key匹配规则，例如 dish_*
     */
    private void cleanCache(String pattern) {
        Set keys = redisTemplate.keys(pattern);
        if (keys != null && keys.size() > 0) {
            redisTemplate.delete(keys);
        }
    }
}
