package com.wuho.erroralert.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class SqlCaptureStatementInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
        STATEMENTS.get().add(sql);
        return sql;
    }

    public static void clear() {
        STATEMENTS.get().clear();
    }

    public static List<String> selectStatements() {
        return STATEMENTS.get().stream()
                .map(String::trim)
                .filter(sql -> sql.toLowerCase(Locale.ROOT).startsWith("select"))
                .toList();
    }
}
