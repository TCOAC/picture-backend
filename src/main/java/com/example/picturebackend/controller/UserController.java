package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.picturebackend.annotation.AuthCheck;
import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.common.DeleteRequest;
import com.example.picturebackend.common.ResultUtils;
import com.example.picturebackend.constant.UserConstant;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.entity.UserLoginRequest;
import com.example.picturebackend.model.tdo.user.UserAddRequest;
import com.example.picturebackend.model.tdo.user.UserQueryRequest;
import com.example.picturebackend.model.tdo.user.UserRegisterRequest;
import com.example.picturebackend.model.tdo.user.UserUpdateRequest;
import com.example.picturebackend.model.tdo.user.VipCodeIssueRequest;
import com.example.picturebackend.model.tdo.user.VipExchangeRequest;
import com.example.picturebackend.model.vo.LoginUserVO;
import com.example.picturebackend.model.vo.UserVO;
import com.example.picturebackend.service.UserService;
import com.example.picturebackend.service.VipCodeService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private VipCodeService vipCodeService;

    @PostMapping("/vip/issue")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> issueVipCode(@RequestBody VipCodeIssueRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getDurationDays() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(vipCodeService.issue(body.getDurationDays(), userService.getLoginUser(request).getId()));
    }

    @PostMapping("/exchange/vip")
    public BaseResponse<LoginUserVO> exchangeVip(@RequestBody VipExchangeRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(vipCodeService.redeem(body.getVipCode(), userService.getLoginUser(request).getId()));
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }


    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }


    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/admin")
    public BaseResponse<Boolean> adminAction() {
        return ResultUtils.success(true);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        ThrowUtils.throwIf(userAddRequest.getUserAccount()==null || !userAddRequest.getUserAccount().matches("[A-Za-z0-9_]{4,32}"),ErrorCode.PARAMS_ERROR,"账号必须为 4-32 位字母、数字或下划线");
        ThrowUtils.throwIf(userAddRequest.getUserRole()!=null && !java.util.Arrays.asList("user","admin").contains(userAddRequest.getUserRole()),ErrorCode.PARAMS_ERROR,"用户角色不合法");
        String rawPassword=userAddRequest.getUserPassword();
        ThrowUtils.throwIf(rawPassword==null||rawPassword.length()<12||rawPassword.length()>72,ErrorCode.PARAMS_ERROR,"管理员创建账号时须设置 12-72 位初始密码");
        String encryptPassword = userService.getEncryptPassword(rawPassword);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        user.setUserPassword(null);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User current=userService.getLoginUser(request);
        ThrowUtils.throwIf(!userService.isAdmin(current) && !current.getId().equals(id),ErrorCode.NO_AUTH_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || pageSize < 1 || pageSize > 100, ErrorCode.PARAMS_ERROR, "分页范围不合法");
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }


}
