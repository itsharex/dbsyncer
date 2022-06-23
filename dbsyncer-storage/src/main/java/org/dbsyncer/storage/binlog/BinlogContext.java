package org.dbsyncer.storage.binlog;

import org.apache.commons.io.FileUtils;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.storage.binlog.proto.BinlogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class BinlogContext implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final long BINLOG_MAX_SIZE = 256 * 1024 * 1024;

    private static final int BINLOG_EXPIRE_DAYS = 7;

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final Charset DEFAULT_CHARSET = Charset.defaultCharset();

    private static final String BINLOG = "binlog";

    private static final String BINLOG_INDEX = BINLOG + ".index";

    private static final String BINLOG_CONFIG = BINLOG + ".config";

    private List<String> index = new LinkedList<>();

    private String path;

    private File configFile;

    private File indexFile;

    private Binlog config;

    private BinlogPipeline pipeline;

    public BinlogContext(String taskName) throws IOException {
        path = new StringBuilder(System.getProperty("user.dir")).append(File.separatorChar)
                .append("data").append(File.separatorChar)
                .append("binlog").append(File.separatorChar)
                .append(taskName).append(File.separatorChar)
                .toString();
        File dir = new File(path);
        if (!dir.exists()) {
            FileUtils.forceMkdir(dir);
        }

        // binlog.index
        indexFile = new File(path + BINLOG_INDEX);
        // binlog.config
        configFile = new File(path + BINLOG_CONFIG);
        if (!configFile.exists()) {
            // binlog.000001
            config = initBinlogConfig(createNewBinlogName(0));
        }

        // read index
        Assert.isTrue(indexFile.exists(), String.format("The index file '%s' is not exist.", indexFile.getName()));
        index.addAll(FileUtils.readLines(indexFile, DEFAULT_CHARSET));

        // delete index file
        deleteExpiredIndexFile();

        // {"binlog":"binlog.000001","pos":0}
        if (null == config) {
            config = JsonUtil.jsonToObj(FileUtils.readFileToString(configFile, DEFAULT_CHARSET), Binlog.class);
        }

        // no index
        if (CollectionUtils.isEmpty(index)) {
            // binlog.000002
            config = initBinlogConfig(createNewBinlogName(getBinlogIndex(config.getFileName())));
            index.addAll(FileUtils.readLines(indexFile, DEFAULT_CHARSET));
        }

        // 配置文件已失效，取最早的索引文件
        int indexOf = index.indexOf(config.getFileName());
        if (-1 == indexOf) {
            logger.warn("The binlog file '{}' is expired.", config.getFileName());
            config = new Binlog().setFileName(index.get(0));
            write(configFile, JsonUtil.objToJson(config), false);
        }

        pipeline = new BinlogPipeline(new File(path + config.getFileName()), config.getPosition());
        logger.info("BinlogContext initialized with config:{}", JsonUtil.objToJson(config));
    }

    private Binlog initBinlogConfig(String binlogName) throws IOException {
        Binlog config = new Binlog().setFileName(binlogName);
        write(configFile, JsonUtil.objToJson(config), false);
        write(indexFile, binlogName + LINE_SEPARATOR, false);
        write(new File(path + binlogName), "", false);
        return config;
    }

    private void deleteExpiredIndexFile() throws IOException {
        if (CollectionUtils.isEmpty(index)) {
            return;
        }
        Set<String> shouldDelete = new HashSet<>();
        for (String name : index) {
            File file = new File(path + name);
            if (!file.exists()) {
                shouldDelete.add(name);
                logger.info("Delete invalid binlog file '{}'.", name);
                continue;
            }
            if (isExpiredFile(file)) {
                FileUtils.forceDelete(file);
                shouldDelete.add(name);
                logger.info("Delete expired binlog file '{}'.", name);
            }
        }
        if (!CollectionUtils.isEmpty(shouldDelete)) {
            index.removeAll(shouldDelete);
            StringBuilder indexBuilder = new StringBuilder();
            index.forEach(name -> indexBuilder.append(name).append(LINE_SEPARATOR));
            write(indexFile, indexBuilder.toString(), false);
        }
    }

    private boolean isExpiredFile(File file) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        Instant instant = attr.creationTime().toInstant();
        return Timestamp.from(instant).getTime() < Timestamp.valueOf(LocalDateTime.now().minusDays(BINLOG_EXPIRE_DAYS)).getTime();
    }

    private String createNewBinlogName(int index) {
        return String.format("%s.%06d", BINLOG, index % 999999 + 1);
    }

    private int getBinlogIndex(String binlogName) {
        return NumberUtil.toInt(StringUtil.substring(binlogName, BINLOG.length() + 1));
    }

    public void flush() throws IOException {
        config.setFileName(pipeline.getBinlogName());
        config.setPosition(pipeline.getOffset());
        write(configFile, JsonUtil.objToJson(config), false);
    }

    public byte[] readLine() throws IOException {
        return pipeline.readLine();
    }

    public void write(BinlogMessage message) throws IOException {
        pipeline.write(message);
    }

    private void write(File file, String line, boolean append) throws IOException {
        FileUtils.write(file, line, DEFAULT_CHARSET, append);
    }

    @Override
    public void close() {
        pipeline.close();
    }
}