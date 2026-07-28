package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.ReportDefinition;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class TransactionalDatasetQueryServiceTest {

    @Test
    void declaresReadOnlyRepeatableReadTransactionThatRollsBackForExceptions()
            throws Exception {
        Method executeAll = TransactionalDatasetQueryService.class.getMethod(
                "executeAll", ReportDefinition.class, Map.class);

        Transactional transactional =
                executeAll.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation())
                .isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(transactional.rollbackFor())
                .containsExactly(Exception.class);
    }
}
