package com.howe.web.controller.system;

import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.ai.storage.AiImageCompressor;
import com.howe.common.annotation.Log;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.enums.BusinessType;
import com.howe.common.exception.file.InvalidExtensionException;
import com.howe.common.storage.StorageService;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.file.FileUploadUtils;
import com.howe.common.utils.file.MimeTypeUtils;
import com.howe.common.utils.uuid.IdUtils;
import com.howe.framework.web.service.TokenService;
import com.howe.system.service.ISysUserService;

/**
 * 个人信息 业务处理
 *
 * @author howe
 */
@Tag(name = "个人中心", description = "当前登录用户的资料查看、资料修改、改密与头像上传")
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StorageService storageService;

    /**
     * 个人信息
     */
    @Operation(summary = "获取个人信息", description = "返回当前登录用户的资料及其角色组、岗位组")
    @GetMapping
    public AjaxResult profile()
    {
        LoginUser loginUser = getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success(user);
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUsername()));
        ajax.put("postGroup", userService.selectUserPostGroup(loginUser.getUsername()));
        return ajax;
    }

    /**
     * 修改用户
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改个人信息", description = "仅修改昵称、邮箱、手机号与性别，手机号与邮箱不可重复")
    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user)
    {
        LoginUser loginUser = getLoginUser();
        SysUser currentUser = loginUser.getUser();
        currentUser.setNickName(user.getNickName());
        currentUser.setEmail(user.getEmail());
        currentUser.setPhonenumber(user.getPhonenumber());
        currentUser.setSex(user.getSex());
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        if (userService.updateUserProfile(currentUser) > 0)
        {
            // 更新缓存用户信息
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改个人密码", description = "校验旧密码，新密码不能与旧密码相同")
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@RequestBody Map<String, String> params)
    {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        LoginUser loginUser = getLoginUser();
        Long userId = loginUser.getUserId();
        SysUser user = userService.selectUserById(userId);
        String password = user.getPassword();
        if (!SecurityUtils.matchesPassword(oldPassword, password))
        {
            return error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(newPassword, password))
        {
            return error("新密码不能与旧密码相同");
        }
        newPassword = SecurityUtils.encryptPassword(newPassword);
        if (userService.resetUserPwd(userId, newPassword) > 0)
        {
            // 更新缓存用户密码&密码最后更新时间
            loginUser.getUser().setPwdUpdateDate(DateUtils.getNowDate());
            loginUser.getUser().setPassword(newPassword);
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @Operation(summary = "上传用户头像", description = "上传前压缩图片，成功后更新用户头像并清理旧头像文件，返回头像访问地址")
    @PostMapping("/avatar")
    public AjaxResult avatar(@Parameter(description = "头像文件") @RequestParam("avatarfile") MultipartFile file) throws Exception
    {
        if (!file.isEmpty())
        {
            LoginUser loginUser = getLoginUser();
            String avatar = storeCompressedAvatar(file);
            if (userService.updateUserAvatar(loginUser.getUserId(), avatar))
            {
                String oldAvatar = loginUser.getUser().getAvatar();
                if (StringUtils.isNotEmpty(oldAvatar))
                {
                    // 交由存储层清理旧头像：R2 走 DeleteObject，本地磁盘走文件删除
                    storageService.remove(oldAvatar);
                }
                AjaxResult ajax = AjaxResult.success();
                ajax.put("imgUrl", avatar);
                // 更新缓存用户头像
                loginUser.getUser().setAvatar(avatar);
                tokenService.setLoginUser(loginUser);
                return ajax;
            }
        }
        return error("上传图片异常，请联系管理员");
    }

    /**
     * 压缩并转存头像
     *
     * <p>上传的原始头像常有数 MB，直接转存既浪费对象存储又拖慢各端加载。这里沿用既有图片校验后，
     * 用 {@link AiImageCompressor} 压缩（长边缩到 1024px 并按质量阶梯压成 JPEG，有透明通道则保留 PNG，
     * 目标 ≤ 500KB），再以压缩后的后缀写入对象存储并返回可访问地址。无法解码时原样转存，
     * 压缩失败不影响上传。</p>
     *
     * @param file 上传的头像文件
     * @return 图床可访问地址（R2 模式）或 {@code /profile} 相对路径（本地存储模式）
     * @throws IOException 读取上传流出错
     * @throws InvalidExtensionException 非图片扩展名
     */
    private String storeCompressedAvatar(MultipartFile file) throws IOException, InvalidExtensionException {
        // 沿用既有校验：仅允许图片扩展名、限制文件大小
        FileUploadUtils.assertAllowed(file, MimeTypeUtils.IMAGE_EXTENSION);
        AiImageCompressor.Compressed compressed = AiImageCompressor.compress(
                file.getBytes(), FileUploadUtils.getExtension(file));
        String objectKey = DateUtils.datePath() + "/" + IdUtils.fastSimpleUUID() + "." + compressed.extension();
        return FileUploadUtils.uploadBytes(YmlConfig.AVATAR_DIR, objectKey,
                compressed.bytes(), MimeTypeUtils.getContentType(compressed.extension()));
    }
}
