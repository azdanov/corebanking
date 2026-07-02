package dev.azdanov.corebanking.account.infrastructure.persistence.mapper;

import dev.azdanov.corebanking.account.infrastructure.persistence.model.AccountRow;
import dev.azdanov.corebanking.account.infrastructure.persistence.model.BalanceRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AccountMapper {

    @Insert("""
        INSERT INTO accounts (id, customer_id, country, created_at)
        VALUES (#{id}, #{customerId}, #{country}, #{createdAt})
        ON CONFLICT (id) DO UPDATE
        SET customer_id = EXCLUDED.customer_id,
            country = EXCLUDED.country
        """)
    void upsertAccount(UUID id, UUID customerId, String country, Instant createdAt);

    @Select("""
        SELECT id, customer_id, country, created_at
        FROM accounts
        WHERE id = #{id}
        """)
    @Results(id = "accountRowMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "customerId", column = "customer_id"),
        @Result(property = "country", column = "country"),
        @Result(property = "createdAt", column = "created_at")
    })
    AccountRow findById(UUID id);

    @Select("""
        SELECT account_id, currency, available_amount
        FROM balances
        WHERE account_id = #{accountId}
        ORDER BY currency
        """)
    @Results(id = "balanceRowMap", value = {
        @Result(property = "accountId", column = "account_id"),
        @Result(property = "currency", column = "currency"),
        @Result(property = "availableAmount", column = "available_amount")
    })
    List<BalanceRow> findBalancesByAccountId(UUID accountId);
}
