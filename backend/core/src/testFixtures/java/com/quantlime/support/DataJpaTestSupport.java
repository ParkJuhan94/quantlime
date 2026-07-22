package com.quantlime.support;

import com.quantlime.common.config.QuerydslConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

// @DataJpaTest는 JPA 관련 자동설정 슬라이스만 로드하고 QuerydslConfig 같은 일반
// @Configuration 빈은 스캔하지 않는다. 그런데 이 슬라이스도 모듈 내 전체
// Repository 빈을 인스턴스화하려 하므로, QueryDSL 커스텀 구현체
// (ScoreQueryRepositoryImpl 등)가 필요로 하는 JPAQueryFactory 빈을 명시적으로
// 끌어와야 한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(QuerydslConfig.class)
public abstract class DataJpaTestSupport extends TestContainerSupport {
}
