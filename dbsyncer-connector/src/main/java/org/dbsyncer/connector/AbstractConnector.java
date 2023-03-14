package org.dbsyncer.connector;

import org.dbsyncer.common.spi.ConnectorMapper;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.config.WriterBatchConfig;
import org.dbsyncer.connector.constant.ConnectorConstant;
import org.dbsyncer.connector.model.Field;
import org.dbsyncer.connector.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractConnector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<Integer, ValueMapper> VALUE_MAPPERS = new LinkedHashMap<>();

    public AbstractConnector() {
        // 常用类型
        VALUE_MAPPERS.putIfAbsent(Types.VARCHAR, new VarcharValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.INTEGER, new IntegerValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.BIGINT, new BigintValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.TIMESTAMP, new TimestampValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.DATE, new DateValueMapper());

        // 较少使用
        VALUE_MAPPERS.putIfAbsent(Types.CHAR, new CharValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.NCHAR, new NCharValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.NVARCHAR, new NVarcharValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.LONGVARCHAR, new LongVarcharValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.NUMERIC, new NumberValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.BINARY, new BinaryValueMapper());

        // 很少使用
        VALUE_MAPPERS.putIfAbsent(Types.SMALLINT, new SmallintValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.TINYINT, new TinyintValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.TIME, new TimeValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.DECIMAL, new DecimalValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.DOUBLE, new DoubleValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.FLOAT, new FloatValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.BIT, new BitValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.BLOB, new BlobValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.CLOB, new ClobValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.NCLOB, new NClobValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.ROWID, new RowIdValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.REAL, new RealValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.VARBINARY, new VarBinaryValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.LONGVARBINARY, new LongVarBinaryValueMapper());
        VALUE_MAPPERS.putIfAbsent(Types.OTHER, new OtherValueMapper());
    }

    /**
     * 转换字段值
     *
     * @param connectorMapper
     * @param config
     */
    protected void convertProcessBeforeWriter(ConnectorMapper connectorMapper, WriterBatchConfig config) {
        if (CollectionUtils.isEmpty(config.getFields()) || CollectionUtils.isEmpty(config.getData())) {
            return;
        }

        // 获取字段映射规则
        for (Map row : config.getData()) {
            // 根据目标字段类型转换值
            for (Field f : config.getFields()) {
                if (null == f) {
                    continue;
                }
                // 根据字段类型转换值
                final ValueMapper valueMapper = VALUE_MAPPERS.get(f.getType());
                if (null != valueMapper) {
                    // 当数据类型不同时，转换值类型
                    try {
                        row.put(f.getName(), valueMapper.convertValue(connectorMapper, row.get(f.getName())));
                    } catch (Exception e) {
                        logger.error("convert value error: ({}, {})", f.getName(), row.get(f.getName()));
                        throw new ConnectorException(e);
                    }
                }
            }
        }
    }

    protected List<Field> getPrimaryKeys(List<Field> fields) {
        List<Field> list = new ArrayList<>();

        for (Field f : fields) {
            if (f.isPk()) {
                list.add(f);
            }
        }
        if (CollectionUtils.isEmpty(list)) {
            throw new ConnectorException("主键为空");
        }
        return list;
    }

    protected boolean isUpdate(String event) {
        return StringUtil.equals(ConnectorConstant.OPERTION_UPDATE, event);
    }

    protected boolean isInsert(String event) {
        return StringUtil.equals(ConnectorConstant.OPERTION_INSERT, event);
    }

    protected boolean isDelete(String event) {
        return StringUtil.equals(ConnectorConstant.OPERTION_DELETE, event);
    }
}