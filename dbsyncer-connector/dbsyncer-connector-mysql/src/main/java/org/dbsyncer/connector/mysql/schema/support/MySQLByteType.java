/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.DataType;
import org.dbsyncer.sdk.schema.support.ByteType;

import java.util.Map;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2024-11-26 22:59
 */
public class MySQLByteType extends ByteType {
    @Override
    protected Byte merge(Object val, Field field) {
        return 0;
    }

    @Override
    protected Byte getDefaultMergedVal() {
        return 0;
    }

    @Override
    protected Object convert(Object val, Field field) {
        return null;
    }

    @Override
    protected Object getDefaultConvertedVal() {
        return null;
    }

    @Override
    public void postProcessBeforeInitialization(Map<String, DataType> mapping) {
        mapping.put("TINYINT", this);
    }
}