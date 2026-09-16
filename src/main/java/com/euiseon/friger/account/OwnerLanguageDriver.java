package com.euiseon.friger.account;

import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.springframework.stereotype.Component;

/** Bind the server identity before MyBatis creates its cache key and JDBC parameters. */
@Component
public class OwnerLanguageDriver extends XMLLanguageDriver {
    private final CurrentUser currentUser;
    public OwnerLanguageDriver(CurrentUser currentUser) { this.currentUser = currentUser; }
    @Override public SqlSource createSqlSource(Configuration config, XNode script, Class<?> type) {
        return scoped(super.createSqlSource(config, script, type));
    }
    @Override public SqlSource createSqlSource(Configuration config, String script, Class<?> type) {
        return scoped(super.createSqlSource(config, script, type));
    }
    private SqlSource scoped(SqlSource source) {
        return parameter -> {
            var bound = source.getBoundSql(parameter);
            if (bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("_userId")))
                bound.setAdditionalParameter("_userId", currentUser.id());
            return bound;
        };
    }
}
