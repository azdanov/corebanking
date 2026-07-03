package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import dev.azdanov.corebanking.account.infrastructure.persistence.model.AccountRow;
import dev.azdanov.corebanking.account.infrastructure.persistence.model.BalanceRow;
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
public interface AccountMapper {

    @Insert("""
        INSERT INTO accounts (id, customer_id, country, created_at, updated_at)
        VALUES (#{id}, #{customerId}, #{country}, #{createdAt}, #{updatedAt})
        """)
    void insert(
        @Param("id") UUID id,
        @Param("customerId") UUID customerId,
        @Param("country") String country,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );

    @Arg(column = "id", javaType = UUID.class)
    @Arg(column = "customer_id", javaType = UUID.class)
    @Arg(column = "country", javaType = String.class)
    @Arg(column = "created_at", javaType = Instant.class)
    @Select("""
        SELECT id, customer_id, country, created_at
        FROM accounts
        WHERE id = #{id}
        """)
    AccountRow findById(@Param("id") UUID id);

    @Arg(column = "account_id", javaType = UUID.class)
    @Arg(column = "currency", javaType = String.class)
    @Arg(column = "available_amount", javaType = BigDecimal.class)
    @Select("""
        SELECT account_id, currency, available_amount
        FROM balances
        WHERE account_id = #{accountId}
        ORDER BY currency
        """)
    List<BalanceRow> findBalancesByAccountId(@Param("accountId") UUID accountId);
}
