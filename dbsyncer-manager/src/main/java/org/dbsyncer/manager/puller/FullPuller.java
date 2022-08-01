package org.dbsyncer.manager.puller;

import org.dbsyncer.manager.Manager;
import org.dbsyncer.parser.Parser;
import org.dbsyncer.parser.event.FullRefreshEvent;
import org.dbsyncer.parser.logger.LogService;
import org.dbsyncer.parser.logger.LogType;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全量同步
 *
 * @author AE86
 * @version 1.0.0
 * @date 2020/04/26 15:28
 */
@Component
public class FullPuller extends AbstractPuller implements ApplicationListener<FullRefreshEvent> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Parser parser;

    @Autowired
    private Manager manager;

    @Autowired
    private LogService logService;

    private Map<String, Task> map = new ConcurrentHashMap<>();

    @Override
    public void start(Mapping mapping) {
        FullWorker worker = new FullWorker(mapping);
        worker.setName(new StringBuilder("full-worker-").append(mapping.getId()).toString());
        worker.setDaemon(false);
        worker.start();
    }

    @Override
    public void close(String metaId) {
        Task task = map.get(metaId);
        if (null != task) {
            task.stop();
        }
    }

    @Override
    public void onApplicationEvent(FullRefreshEvent event) {
        // 异步监听任务刷新事件
        flush(event.getTask());
    }

    private void doTask(Task task, Mapping mapping, List<TableGroup> list, ExecutorService executorService) {
        // 记录开始时间
        long now = Instant.now().toEpochMilli();
        task.setBeginTime(now);
        task.setEndTime(now);
        flush(task);

        for (TableGroup t : list) {
            if (!task.isRunning()) {
                break;
            }
            parser.execute(task, mapping, t, executorService);
        }

        // 记录结束时间
        task.setEndTime(Instant.now().toEpochMilli());
        flush(task);
    }

    private void flush(Task task) {
        Meta meta = manager.getMeta(task.getId());
        Assert.notNull(meta, "检查meta为空.");

        meta.setBeginTime(task.getBeginTime());
        meta.setEndTime(task.getEndTime());
        manager.editMeta(meta);
    }

    final class FullWorker extends Thread {
        Mapping mapping;
        List<TableGroup> list;

        public FullWorker(Mapping mapping) {
            this.mapping = mapping;
            this.list = manager.getTableGroupAll(mapping.getId());
            Assert.notEmpty(list, "映射关系不能为空");
        }

        @Override
        public void run() {
            final String metaId = mapping.getMetaId();
            logger.info("开始全量同步：{}, {}", metaId, mapping.getName());
            try {
                map.putIfAbsent(metaId, new Task(metaId));
                final ExecutorService executor = Executors.newFixedThreadPool(mapping.getThreadNum());
                Task task = map.get(metaId);
                doTask(task, mapping, list, executor);
            } catch (Exception e) {
                logger.error(e.getMessage());
                logService.log(LogType.SystemLog.ERROR, e.getMessage());
            } finally {
                map.remove(metaId);
                publishClosedEvent(metaId);
                logger.info("结束全量同步：{}, {}", metaId, mapping.getName());
            }
        }
    }

}