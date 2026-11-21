package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.entity.StaffGrant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 授权简单查询复用 BaseMapper；关联目录、管理员保护及原子授权写入保留专用 SQL。 */
@Mapper
public interface AdminMapper extends BaseMapper<StaffGrant> {
    // region 查询结果类型：内部投影，不直接作为 HTTP 响应

    /** 账号目录投影，明确排除密码哈希。 */
    record StaffRow(long id, String displayName, String identifier, String status) {}

    /** 单个角色在指定范围可授予的动作上限。 */
    record Ceiling(String scopeType, String permissionCode, String permissionLabel) {}

    /** 批量目录读取时保留角色归属，供 Service 按角色分组。 */
    record RoleCeiling(
            String roleCode, String scopeType, String permissionCode, String permissionLabel) {
        public Ceiling ceiling() {
            return new Ceiling(scopeType, permissionCode, permissionLabel);
        }
    }

    // endregion

    // region 管理员保护

    /** 锁定固定角色行，使初始化和管理员变更串行，须在事务内调用。 */
    String lockAdministratorRole();

    /** 包含历史撤销记录，防止删除管理入口后重新走首位管理员初始化。 */
    default long administratorGrantCount() {
        return selectCount(
                Wrappers.<StaffGrant>lambdaQuery().eq(StaffGrant::getRoleCode, "SYSTEM_ADMIN"));
    }

    /** 统计同时具有账号写入和管理员管理动作的启用管理员。 */
    long controllingAdministratorCount();

    // endregion

    // region 内部账号目录及状态

    /** keyword 须已按 LIKE ESCAPE '!' 转义，分页已由 PageQuery 校验。 */
    List<StaffRow> staff(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") long offset);

    /** 与 staff 复用完全相同的筛选片段，保证条目和总数口径一致。 */
    long staffCount(@Param("keyword") String keyword, @Param("status") String status);

    /** 停用时递增会话版本，使重新启用账号后旧会话仍不可用。 */
    void updateStaff(
            @Param("id") long id, @Param("name") String name, @Param("status") String status);

    // endregion

    // region 角色与权限字典

    List<Ceiling> ceilings(@Param("role") String role);

    /** 一次读取当前页全部角色上限，避免目录组装时按角色循环查库。 */
    List<RoleCeiling> ceilingsForRoles(@Param("roles") List<String> roles);

    /** 仅供系统账号管理授权选项，返回基础名称，不授予读取考试业务内容的权限。 */
    List<AdminResponses.NamedOption> projectOptions();

    List<AdminResponses.NamedOption> sessionOptions();

    // endregion

    // region 逐条授权写入：调用前必须完成动作、范围和操作者校验

    /** 保留历史记录，通过 revoked_at 撤销当前授权。 */
    void revokeAll(@Param("actor") long actor, @Param("target") long target);

    /** 按角色与范围的唯一键插入或恢复授权，恢复后应整体替换其动作。 */
    void upsertGrant(
            @Param("actor") long actor,
            @Param("target") long target,
            @Param("role") String role,
            @Param("scope") String scope,
            @Param("project") Long project,
            @Param("session") Long session);

    /** key 对应生成列 scope_key：项目/场次 ID，系统和目录范围为 0。 */
    default long grantId(long target, String role, String scope, long key) {
        StaffGrant grant =
                selectOne(
                        Wrappers.<StaffGrant>lambdaQuery()
                                .select(StaffGrant::getId)
                                .eq(StaffGrant::getStaffId, target)
                                .eq(StaffGrant::getRoleCode, role)
                                .eq(StaffGrant::getScopeType, scope)
                                .eq(StaffGrant::getScopeKey, key));
        if (grant == null) {
            throw new IllegalStateException("授权写入后未找到对应记录");
        }
        return grant.getId();
    }

    void clearGrantPermissions(@Param("id") long id);

    void grantPermission(
            @Param("id") long id,
            @Param("role") String role,
            @Param("scope") String scope,
            @Param("permission") String permission);

    /** 只判断授权项目是否存在，不据此授予访问该项目的业务权限。 */
    long projectExists(@Param("id") long id);

    // endregion
}
