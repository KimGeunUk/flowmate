package com.flowmate.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.LeaveBalance;

/**
 * 연차 잔여 매퍼. Task 1 시드(사원 18 곽수빈, 2026년, 부여 17.0/사용 5.0)를 전제로 한다.
 *
 * ★ @Transactional 로 각 테스트가 끝나면 롤백되므로, updateUsedDays 로 시드를
 * 바꿔도 다음 테스트나 다음 실행에 흔적이 남지 않는다.
 */
@SpringBootTest
@Transactional
class LeaveBalanceMapperIT {

    @Autowired
    private LeaveBalanceMapper leaveBalanceMapper;

    @Test
    @DisplayName("읽기 전용 조회는 시드된 부여·사용 일수를 그대로 돌려준다")
    void findByEmpIdAndYearReturnsSeededRow() {
        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(18L, 2026);

        assertThat(balance).isNotNull();
        assertThat(balance.getGrantedDays()).isEqualByComparingTo("17.0");
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.0");
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("12.0");
    }

    @Test
    @DisplayName("없는 사원·연도 조합은 null 이다")
    void findByEmpIdAndYearReturnsNullWhenAbsent() {
        assertThat(leaveBalanceMapper.findByEmpIdAndYear(18L, 1999)).isNull();
    }

    @Test
    @DisplayName("★ FOR UPDATE 조회도 같은 값을 돌려주고, 잠근 뒤에도 트랜잭션이 이어져 갱신까지 성공한다")
    void findForUpdateLocksRowWithoutBreakingTransaction() {
        LeaveBalance locked = leaveBalanceMapper.findForUpdate(18L, 2026);
        assertThat(locked).isNotNull();
        assertThat(locked.getUsedDays()).isEqualByComparingTo("5.0");

        // 잠금이 트랜잭션을 중단시키지 않는다는 것을 같은 트랜잭션 안의 후속 쓰기로 증명한다
        int updated = leaveBalanceMapper.updateUsedDays(18L, 2026, new BigDecimal("6.0"));
        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("사용일수 갱신은 왕복된다 — 갱신 후 재조회하면 새 값이 보인다")
    void updateUsedDaysRoundTrips() {
        int updated = leaveBalanceMapper.updateUsedDays(18L, 2026, new BigDecimal("8.5"));
        assertThat(updated).isEqualTo(1);

        LeaveBalance reloaded = leaveBalanceMapper.findByEmpIdAndYear(18L, 2026);
        assertThat(reloaded.getUsedDays()).isEqualByComparingTo("8.5");
        assertThat(reloaded.getRemainingDays()).isEqualByComparingTo("8.5");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
