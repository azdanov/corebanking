package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import dev.azdanov.corebanking.account.infrastructure.persistence.model.TransactionRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TransactionMapper {

    @Insert("""
        INSERT INTO transactions (
            id,
            account_id,
            amount,
            currency,
            direction,
            description,
            balance_after,
            value_time,
            booking_time
        )
        VALUES (
            #{id},
            #{accountId},
            #{amount},
            #{currency},
            #{direction},
            #{description},
            #{balanceAfter},
            #{valueTime},
            #{bookingTime}
        )
        """)
    void insert(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String direction,
        String description,
        BigDecimal balanceAfter,
        Instant valueTime,
        Instant bookingTime
    );

    @Select("""
        SELECT id, account_id, amount, currency, direction, description, balance_after, value_time, booking_time
        FROM transactions
        WHERE account_id = #{accountId}
        ORDER BY booking_time DESC
        """)
    @Results(id = "transactionRowMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "amount", column = "amount"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "direction", column = "direction"),
        @Result(property = "description", column = "description"),
        @Result(property = "balanceAfter", column = "balance_after"),
        @Result(property = "valueTime", column = "value_time"),
        @Result(property = "bookingTime", column = "booking_time")
    })
    List<TransactionRow> findByAccountId(UUID accountId);
}
