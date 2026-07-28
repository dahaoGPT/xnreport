package com.xn.report.sql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadOnlySqlGuardTest {

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    @ParameterizedTest
    @ValueSource(strings = {
        "select * from t",
        " -- report\n SELECT ';' AS semicolon",
        "# mysql comment\r\n/* report */ SELECT col FROM t WHERE name = :name",
        "SELECT 'it''s; safe', \"a;name\", `semi;column` FROM `report`;",
        "SELECT 'DELETE FROM t' AS statement_text /* UPDATE ignored */ FROM audit",
        "SELECT last_update AS update_time FROM audit -- trailing comment\n;"
    })
    void acceptsSingleSelect(String sql) {
        guard.validate(sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   /* only comment */ ",
        "update t set a = 1",
        "select 1; delete from t",
        "select 1;;",
        "select 1; select 2",
        "call rebuild_report()",
        "with x as (select 1) select * from x",
        "CREATE TABLE copy AS SELECT * FROM t",
        "SELECT * FROM t FOR UPDATE",
        "SELECT * FROM t INTO OUTFILE '/tmp/report.csv'",
        "SELECT * FROM t INTO DUMPFILE '/tmp/report.bin'",
        "SELECT LOAD_FILE('/tmp/secret')",
        "SELECT 1 /*!50000 INTO OUTFILE '/tmp/report.csv' */",
        "SELECT 'unterminated",
        "SELECT 1 /* unterminated"
    })
    void rejectsUnsupportedOrUnsafeSql(String sql) {
        assertThatThrownBy(() -> guard.validate(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotTreatDoubleDashWithoutFollowingWhitespaceAsComment() {
        guard.validate("SELECT 1--1");
    }

    @Test
    void rejectsNormalStateDangerousTokensWithoutSubstringFalsePositives() {
        guard.validate("SELECT updated_at, deleted_flag, callback FROM audit");

        assertThatThrownBy(() -> guard.validate("SELECT 1 FROM audit DELETE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE");
    }

    @Test
    void backslashDoesNotEscapeBacktickAndHideASecondStatement() {
        String sql = "SELECT `safe\\`; DELETE FROM t -- `";

        assertThatThrownBy(() -> guard.validate(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT 'x\\' INTO OUTFILE '/tmp/leak' -- '",
        "SELECT \"x\\\" INTO OUTFILE '/tmp/leak' -- \""
    })
    void rejectsSqlUnsafeUnderAlternateMysqlLexerModes(String sql) {
        assertThatThrownBy(() -> guard.validate(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
