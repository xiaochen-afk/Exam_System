package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.auth.entity.UserAccount;
import com.examsystem.auth.model.AuthData;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 账号 CRUD 复用 BaseMapper；行锁及逐条授权关联保留专用 XML，权限决策仍在 Service。 */
@Mapper
public interface AuthMapper extends BaseMapper<UserAccount> {
    // region 账号快照与行锁

    /** 账号实体不直接进入会话；不存在时返回 null，普通账号表不包含密码字段。 */
    default AuthData.User user(long id) {
        UserAccount row = selectById(id);
        return row == null ? null : row.snapshot();
    }

    /** 锁定账号行；调用方必须在事务内按统一 ID 顺序获取锁。 */
    AuthData.User lockUser(@Param("id") long id);

    // endregion

    // region 有效授权关联

    /** 每个动作保留所属授权及范围，禁止跨授权拼接动作和作用域。 */
    List<AuthData.GrantAction> actions(@Param("id") long id);

    /** 批量读取账号授权，避免列表逐账号查询；空集合返回空结果。 */
    List<AuthData.OwnedGrantAction> actionsForUsers(@Param("ids") List<Long> ids);

    // endregion
}
