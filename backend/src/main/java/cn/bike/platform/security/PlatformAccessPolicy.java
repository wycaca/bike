package cn.bike.platform.security;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.ops.OperationsModels.TaskItem;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 统一执行组织和城市的数据范围校验，避免控制器只校验角色而遗漏资源归属。 */
@Component
public class PlatformAccessPolicy {

    /** 输入: 当前用户和目标城市; 输出: 无，越权时抛出 403 异常。 */
    public void requireCity(PlatformPrincipal principal, String cityCode) {
        if (principal.role() == UserRole.OPERATOR && !Objects.equals(principal.cityCode(), cityCode)) {
            throw new AccessDeniedException("运维人员只能访问所属城市的数据");
        }
    }

    /** 输入: 当前用户和任务; 输出: 无，运维人员只能访问本组织任务。 */
    public void requireTask(PlatformPrincipal principal, TaskItem task) {
        requireCity(principal, task.cityCode());
        if (principal.role() == UserRole.OPERATOR && !Objects.equals(principal.orgId(), task.orgId())) {
            throw new AccessDeniedException("运维人员只能访问所属组织的任务");
        }
    }
}
