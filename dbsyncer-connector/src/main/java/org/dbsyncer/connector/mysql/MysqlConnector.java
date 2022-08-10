package org.dbsyncer.connector.mysql;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.config.ReaderConfig;
import org.dbsyncer.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.connector.database.DatabaseConnectorMapper;
import org.dbsyncer.connector.model.PageSql;
import org.dbsyncer.connector.model.Table;

import java.util.List;

public final class MysqlConnector extends AbstractDatabaseConnector {

    @Override
    protected String buildSqlWithQuotation() {
        return "`";
    }

    @Override
    public String getPageSql(PageSql config) {
        final String quotation = buildSqlWithQuotation();
        final String pk = config.getPk();
        // select * from test.`my_user` where `id` > ? order by `id` limit ?
        StringBuilder querySql = new StringBuilder(config.getQuerySql());
        String queryFilter = config.getSqlBuilderConfig().getQueryFilter();
        if (StringUtil.isNotBlank(queryFilter)) {
            querySql.append(" AND ");
        }else {
            querySql.append(" WHERE ");
        }
        querySql.append(quotation).append(pk).append(quotation).append(" > ? ORDER BY ").append(quotation).append(pk).append(quotation).append(" LIMIT ?");
        return querySql.toString();
    }

    @Override
    public String getPageCursorSql(PageSql config){
        final String quotation = buildSqlWithQuotation();
        final String pk = config.getPk();
        // select * from test.`my_user` order by `id` limit ?
        StringBuilder querySql = new StringBuilder(config.getQuerySql()).append(" ORDER BY ").append(quotation).append(pk).append(quotation).append(" LIMIT ?");
        return querySql.toString();
    }

    @Override
    public Object[] getPageArgs(ReaderConfig config) {
        int pageSize = config.getPageSize();
        String cursor = config.getCursor();
        if (StringUtil.isBlank(cursor)) {
            return new Object[] {pageSize};
        }
        return new Object[] {cursor, pageSize};
    }

    @Override
    protected boolean enableCursor() {
        return true;
    }

    @Override
    public List<Table> getTable(DatabaseConnectorMapper connectorMapper) {
        return super.getTable(connectorMapper, "show tables");
    }

}