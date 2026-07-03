package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import dev.azdanov.corebanking.account.infrastructure.persistence.model.TransactionRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
            created_at
        )
        VALUES (
            #{id},
            #{accountId},
            #{amount},
            #{currency},
            CAST(#{direction} AS transaction_direction),
            #{description},
            #{balanceAfter},
            #{createdAt}
        )
        """)
    void insert(
        @Param("id") UUID id,
        @Param("accountId") UUID accountId,
        @Param("amount") BigDecimal amount,
        @Param("currency") String currency,
        @Param("direction") String direction,
        @Param("description") String description,
        @Param("balanceAfter") BigDecimal balanceAfter,
        @Param("createdAt") Instant createdAt
    );

    @Arg(column = "id", javaType = UUID.class)
    @Arg(column = "account_id", javaType = UUID.class)
    @Arg(column = "amount", javaType = BigDecimal.class)
    @Arg(column = "currency", javaType = String.class)
    @Arg(column = "direction", javaType = String.class)
    @Arg(column = "description", javaType = String.class)
    @Arg(column = "balance_after", javaType = BigDecimal.class)
    @Arg(column = "created_at", javaType = Instant.class)
    @Select("""
        SELECT id, account_id, amount, currency, direction, description, balance_after, created_at
        FROM transactions
        WHERE account_id = #{accountId}
        ORDER BY created_at DESC
        """)
    List<TransactionRow> findByAccountId(@Param("accountId") UUID accountId);
}
