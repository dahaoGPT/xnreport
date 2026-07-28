package com.xn.report.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.xn.report.config.ReportDefinition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    @Test
    void doesNotExposeExecutionObserverAsPublicApi() {
        Constructor<?>[] publicConstructors =
                TransactionalDatasetQueryService.class.getConstructors();

        assertThat(publicConstructors).hasSize(1);
        assertThat(publicConstructors[0].getParameterTypes()).hasSize(6);
        assertThat(Modifier.isPublic(
                DatasetExecutionObserver.class.getModifiers())).isFalse();
    }
}
