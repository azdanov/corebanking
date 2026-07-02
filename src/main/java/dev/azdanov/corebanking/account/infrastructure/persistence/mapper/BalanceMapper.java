package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BalanceMapper {

    @Insert({
        "<script>",
        "INSERT INTO balances (account_id, currency, available_amount)",
        "VALUES",
        "<foreach collection='currencies' item='currency' separator=','>",
        "(#{accountId}, #{currency}, 0)",
        "</foreach>",
        "ON CONFLICT (account_id, currency) DO NOTHING",
        "</script>"
    })
    void insertInitialBalances(@Param("accountId") UUID accountId, @Param("currencies") List<String> currencies);

    @Update("""
        UPDATE balances
        SET available_amount = available_amount + #{amount}
        WHERE account_id = #{accountId}
          AND currency = #{currency}
        """)
    int incrementBalance(UUID accountId, String currency, BigDecimal amount);

    @Update("""
        UPDATE balances
        SET available_amount = available_amount - #{amount}
        WHERE account_id = #{accountId}
          AND currency = #{currency}
          AND available_amount >= #{amount}
        """)
    int decrementBalanceIfEnough(UUID accountId, String currency, BigDecimal amount);

    @Select("""
        SELECT available_amount
        FROM balances
        WHERE account_id = #{accountId}
          AND currency = #{currency}
        """)
    BigDecimal findAvailableAmount(UUID accountId, String currency);
}
