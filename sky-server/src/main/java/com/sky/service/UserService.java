package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.Dish;
import com.sky.entity.User;
import com.sky.vo.DishVO;

import java.util.List;

public interface UserService {

    /**
     * 微信登录：用临时code换取openid，已注册用户直接返回，新用户自动注册后返回
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    List<DishVO> listWithFlavor(Dish dish);
}
