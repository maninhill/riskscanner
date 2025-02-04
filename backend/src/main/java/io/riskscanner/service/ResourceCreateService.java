package io.riskscanner.service;

import com.fit2cloud.quartz.anno.QuartzScheduled;
import com.google.gson.Gson;
import io.riskscanner.base.domain.*;
import io.riskscanner.base.mapper.*;
import io.riskscanner.base.mapper.ext.ExtScanHistoryMapper;
import io.riskscanner.commons.constants.CloudAccountConstants;
import io.riskscanner.commons.constants.CommandEnum;
import io.riskscanner.commons.constants.TaskConstants;
import io.riskscanner.commons.utils.*;
import io.riskscanner.controller.request.resource.ResourceRequest;
import io.riskscanner.dto.ResourceDTO;
import io.riskscanner.i18n.Translator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.fastjson.JSON.toJSONString;

/**
 * @author maguohao
 */
@Service
public class ResourceCreateService {
    // 只有一个任务在处理，防止超配
    private static ConcurrentHashMap<String, String> processingGroupIdMap = new ConcurrentHashMap<>();
    @Resource
    private TaskMapper taskMapper;
    @Resource
    private CommonThreadPool commonThreadPool;
    @Resource
    private TaskItemMapper taskItemMapper;
    @Resource
    private ResourceService resourceService;
    @Resource
    private RuleService ruleService;
    @Resource
    private TaskItemResourceMapper taskItemResourceMapper;
    @Resource
    private AccountMapper accountMapper;
    @Resource
    private ResourceMapper resourceMapper;
    @Resource
    private OrderService orderService;
    @Resource
    private Environment env;
    @Resource
    private ExtScanHistoryMapper extScanHistoryMapper;

    @QuartzScheduled(cron = "${cron.expression.local}")
    public void handleTasks() {
        final TaskExample taskExample = new TaskExample();
        TaskExample.Criteria criteria = taskExample.createCriteria();
        criteria.andStatusEqualTo(TaskConstants.TASK_STATUS.APPROVED.toString());
        if (CollectionUtils.isNotEmpty(processingGroupIdMap.keySet())) {
            criteria.andIdNotIn(new ArrayList<>(processingGroupIdMap.keySet()));
        }
        taskExample.setOrderByClause("create_time limit 10");
        List<Task> taskList = taskMapper.selectByExample(taskExample);
        if (CollectionUtils.isNotEmpty(taskList)) {
            taskList.forEach(task -> {
                LogUtil.info("handling task: {}", toJSONString(task));
                final Task taskToBeProceed = BeanUtils.copyBean(new Task(), task);
                if (processingGroupIdMap.get(taskToBeProceed.getId()) != null) {
                    return;
                }
                processingGroupIdMap.put(taskToBeProceed.getId(), taskToBeProceed.getId());
                commonThreadPool.addTask(() -> {
                    try {
                        handleTask(taskToBeProceed);
                    } catch (Exception e) {
                        LogUtil.error(e);
                    } finally {
                        processingGroupIdMap.remove(taskToBeProceed.getId());
                    }
                });
            });
        } else {
            AccountExample accountExample = new AccountExample();
            accountExample.createCriteria().andStatusEqualTo(CloudAccountConstants.Status.VALID.name());
            List<AccountWithBLOBs> accountList = accountMapper.selectByExampleWithBLOBs(accountExample);

            accountList.forEach(account -> orderService.insertScanHistory(account));
        }
    }

    public void handleTask(Task task) {
        String taskId = task.getId();
        int i = orderService.updateTaskStatus(taskId, TaskConstants.TASK_STATUS.APPROVED.toString(), TaskConstants.TASK_STATUS.PROCESSING.toString());
        if (i == 0) {
            return;
        }
        try {
            TaskItemExample taskItemExample = new TaskItemExample();
            taskItemExample.createCriteria().andTaskIdEqualTo(taskId);
            List<TaskItemWithBLOBs> taskItemWithBLOBs = taskItemMapper.selectByExampleWithBLOBs(taskItemExample);
            int successCount = 0;
            for (TaskItemWithBLOBs taskItem : taskItemWithBLOBs) {
                if (LogUtil.getLogger().isDebugEnabled()) {
                    LogUtil.getLogger().debug("handling taskItem: {}", toJSONString(taskItem));
                }
                if (handleTaskItem(BeanUtils.copyBean(new TaskItemWithBLOBs(), taskItem), task)) {
                    successCount++;
                }
            }
            if (!taskItemWithBLOBs.isEmpty() && successCount == 0)
                throw new Exception("Faild to handle all taskItems, taskId: " + task.getId());
            String taskStatus;
            if (StringUtils.equalsIgnoreCase(task.getType(), TaskConstants.TaskType.quartz.name())) {
                taskStatus = TaskConstants.TASK_STATUS.RUNNING.toString();
            } else {
                taskStatus = TaskConstants.TASK_STATUS.FINISHED.toString();
            }
            if (successCount != taskItemWithBLOBs.size()) {
                taskStatus = TaskConstants.TASK_STATUS.WARNING.toString();
            }
            orderService.updateTaskStatus(taskId, null, taskStatus);

        } catch (Exception e) {
            orderService.updateTaskStatus(taskId, null, TaskConstants.TASK_STATUS.ERROR.name());
            LogUtil.error("handleTask, taskId: " + taskId, e);
        }
        ScanTaskHistoryExample example = new ScanTaskHistoryExample();
        example.createCriteria().andTaskIdEqualTo(task.getId()).andScanIdEqualTo(extScanHistoryMapper.getScanId(task.getAccountId()));
        orderService.updateTaskHistory(task, example);
    }

    private boolean handleTaskItem(TaskItemWithBLOBs taskItem, Task task) {
        orderService.updateTaskItemStatus(taskItem.getId(), TaskConstants.TASK_STATUS.PROCESSING);
        try {
            Thread.sleep(1000);//手动休眠1秒，云平台调用太快容易出达到最大连接数而超时
            for (int i = 0; i < taskItem.getCount(); i++) {
                createResource(taskItem, task);
            }
            orderService.updateTaskItemStatus(taskItem.getId(), TaskConstants.TASK_STATUS.FINISHED);
            return true;
        } catch (Exception e) {
            orderService.updateTaskItemStatus(taskItem.getId(), TaskConstants.TASK_STATUS.ERROR);
            LogUtil.error("handleTaskItem, taskItemId: " + taskItem.getId(), e);
            return false;
        }
    }

    private void createResource(TaskItemWithBLOBs taskItem, Task task) throws Exception {
        LogUtil.info("createResource for taskItem: {}", toJSONString(taskItem));
        String operation = Translator.get("i18n_create_resource");
        String resultStr = "";
        try {
            String dirPath = TaskConstants.RESULT_FILE_PATH_PREFIX + task.getId() + "/" + taskItem.getRegionId();
            Map<String, String> map = PlatformUtils.getAccount(accountMapper.selectByPrimaryKey(taskItem.getAccountId()), taskItem.getRegionId());
            String command = PlatformUtils.fixedCommand(CommandEnum.custodian.getCommand(), CommandEnum.run.getCommand(), dirPath, "policy.yml", map);
            LogUtil.info(task.getId() + " {}[command]: " + command);
            CommandUtils.saveAsFile(taskItem.getDetails(), task.getId() + "/" + taskItem.getRegionId());//重启服务后容器内文件在/tmp目录下会丢失
            resultStr = CommandUtils.commonExecCmdWithResult(command, dirPath);
            if (LogUtil.getLogger().isDebugEnabled()) {
                LogUtil.getLogger().debug("resource created: {}", resultStr);
            }
            if (resultStr.contains("ERROR"))
                throw new Exception(Translator.get("i18n_create_resource_failed") + ": " + resultStr);

            TaskItemResourceExample example = new TaskItemResourceExample();
            example.createCriteria().andTaskIdEqualTo(task.getId()).andTaskItemIdEqualTo(taskItem.getId());
            List<TaskItemResourceWithBLOBs> list = taskItemResourceMapper.selectByExampleWithBLOBs(example);
            for (TaskItemResourceWithBLOBs taskItemResource : list) {

                String resourceType = taskItemResource.getResourceType();
                String resourceName = taskItemResource.getResourceName();
                String taskItemId = taskItem.getId();
                if (StringUtils.equals(task.getType(), TaskConstants.TaskType.manual.name()))
                    orderService.saveTaskItemLog(taskItemId, "resourceType", Translator.get("i18n_operation_begin") + ": " + operation, StringUtils.EMPTY, true);
                Rule rule = ruleService.getRuleById(taskItem.getRuleId());
                if (rule == null) {
                    orderService.saveTaskItemLog(taskItemId, taskItemId, Translator.get("i18n_operation_ex") + ": " + operation, Translator.get("i18n_ex_rule_not_exist"), false);
                    throw new Exception(Translator.get("i18n_ex_rule_not_exist") + ":" + taskItem.getRuleId());
                }
                String custodianRun = ReadFileUtils.readToBuffer(dirPath + "/" + taskItemResource.getDirName() + "/" + TaskConstants.CUSTODIAN_RUN_RESULT_FILE);
                String metadata = ReadFileUtils.readJsonFile(dirPath + "/" + taskItemResource.getDirName() + "/", TaskConstants.METADATA_RESULT_FILE);
                String resources = ReadFileUtils.readJsonFile(dirPath + "/" + taskItemResource.getDirName() + "/", TaskConstants.RESOURCES_RESULT_FILE);

                ResourceWithBLOBs resourceWithBLOBs = new ResourceWithBLOBs();
                if (taskItemResource.getResourceId() != null) {
                    resourceWithBLOBs = resourceMapper.selectByPrimaryKey(taskItemResource.getResourceId());
                }
                resourceWithBLOBs.setCustodianRunLog(custodianRun);
                resourceWithBLOBs.setMetadata(metadata);
                resourceWithBLOBs.setResources(resources);
                resourceWithBLOBs.setResourceName(resourceName);
                resourceWithBLOBs.setDirName(taskItemResource.getDirName());
                resourceWithBLOBs.setResourceType(resourceType);
                resourceWithBLOBs.setAccountId(taskItem.getAccountId());
                resourceWithBLOBs.setSeverity(taskItem.getSeverity());
                resourceWithBLOBs.setRegionId(taskItem.getRegionId());
                resourceWithBLOBs.setRegionName(taskItem.getRegionName());
                resourceWithBLOBs.setResourceCommand(taskItemResource.getResourceCommand());
                resourceWithBLOBs.setResourceCommandAction(taskItemResource.getResourceCommandAction());
                ResourceWithBLOBs resource = resourceService.saveResource(resourceWithBLOBs, taskItem, task, taskItemResource);
                LogUtil.info("The returned data is{}: " + new Gson().toJson(resource));
                orderService.saveTaskItemLog(taskItemId, resourceType, Translator.get("i18n_operation_end") + ": " + operation, Translator.get("i18n_cloud_account") + ": " + resource.getPluginName() + "，"
                        + Translator.get("i18n_region") + ": " + resource.getRegionName() + "，" + Translator.get("i18n_rule_type") + ": " + resourceType + "，" + Translator.get("i18n_resource_manage") + ": " + resource.getReturnSum() + "/" + resource.getResourcesSum(), true);
                //执行完删除返回目录文件，以便于下一次操作覆盖
                String deleteResourceDir = "rm -rf " + dirPath;
                CommandUtils.commonExecCmdWithResult(deleteResourceDir, dirPath);
            }

        } catch (Exception e) {
            orderService.saveTaskItemLog(taskItem.getId(), taskItem.getId(), Translator.get("i18n_operation_ex") + ": " + operation, e.getMessage(), false);
            LogUtil.error("createResource, taskItemId: " + taskItem.getId() + ", resultStr:" + resultStr, ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }


    public Map<String, Object> getParameters(String taskId) {
        Map<String, Object> map = new HashMap<>();
        Task task = taskMapper.selectByPrimaryKey(taskId);
        map.put("TASK_DESCRIPTION", task.getDescription());
        ResourceRequest resourceRequest = new ResourceRequest();
        resourceRequest.setTaskId(taskId);
        List<ResourceDTO> list = resourceService.search(resourceRequest);
        if (!CollectionUtils.isEmpty(list)) {
            map.put("RESOURCES", list);
        }
        return map;
    }

}
