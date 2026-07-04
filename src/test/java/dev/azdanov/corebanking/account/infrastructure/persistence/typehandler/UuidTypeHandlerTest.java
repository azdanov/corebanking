package dev.azdanov.corebanking.account.infrastructure.persistence.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UuidTypeHandlerTest {

    private UuidTypeHandler typeHandler;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private CallableStatement callableStatement;

    private UUID testUuid;

    @BeforeEach
    void setUp() {
        typeHandler = new UuidTypeHandler();
        testUuid = UUID.randomUUID();
    }

    @Nested
    class SetNonNullParameter {

        @Test
        void shouldSetUuidParameter() throws SQLException {
            int columnIndex = 1;

            typeHandler.setNonNullParameter(preparedStatement, columnIndex, testUuid, JdbcType.OTHER);

            verify(preparedStatement).setObject(columnIndex, testUuid);
        }
    }

    @Nested
    class GetNullableResultByColumnName {

        @Test
        void shouldReturnNullForNullValue() throws SQLException {
            doReturn(null).when(resultSet).getObject("column_name");

            UUID result = typeHandler.getNullableResult(resultSet, "column_name");

            assertThat(result).isNull();
        }

        @Test
        void shouldConvertStringToUuid() throws SQLException {
            String uuidString = testUuid.toString();
            doReturn(uuidString).when(resultSet).getObject("column_name");

            UUID result = typeHandler.getNullableResult(resultSet, "column_name");

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldReturnUuidWhenValueIsUuid() throws SQLException {
            doReturn(testUuid).when(resultSet).getObject("column_name");

            UUID result = typeHandler.getNullableResult(resultSet, "column_name");

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldThrowExceptionForInvalidType() throws SQLException {
            doReturn(123).when(resultSet).getObject("column_name");

            assertThatThrownBy(() -> typeHandler.getNullableResult(resultSet, "column_name"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Cannot convert class java.lang.Integer to UUID");
        }
    }

    @Nested
    class GetNullableResultByColumnIndex {

        @Test
        void shouldReturnNullForNullValue() throws SQLException {
            int columnIndex = 1;
            doReturn(null).when(resultSet).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(resultSet, columnIndex);

            assertThat(result).isNull();
        }

        @Test
        void shouldConvertStringToUuid() throws SQLException {
            int columnIndex = 1;
            String uuidString = testUuid.toString();
            doReturn(uuidString).when(resultSet).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(resultSet, columnIndex);

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldReturnUuidWhenValueIsUuid() throws SQLException {
            int columnIndex = 1;
            doReturn(testUuid).when(resultSet).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(resultSet, columnIndex);

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldThrowExceptionForInvalidType() throws SQLException {
            int columnIndex = 1;
            doReturn(456).when(resultSet).getObject(columnIndex);

            assertThatThrownBy(() -> typeHandler.getNullableResult(resultSet, columnIndex))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Cannot convert class java.lang.Integer to UUID");
        }
    }

    @Nested
    class GetNullableResultWithCallableStatement {

        @Test
        void shouldReturnNullForNullValue() throws SQLException {
            int columnIndex = 1;
            doReturn(null).when(callableStatement).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(callableStatement, columnIndex);

            assertThat(result).isNull();
        }

        @Test
        void shouldConvertStringToUuid() throws SQLException {
            int columnIndex = 1;
            String uuidString = testUuid.toString();
            doReturn(uuidString).when(callableStatement).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(callableStatement, columnIndex);

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldReturnUuidWhenValueIsUuid() throws SQLException {
            int columnIndex = 1;
            doReturn(testUuid).when(callableStatement).getObject(columnIndex);

            UUID result = typeHandler.getNullableResult(callableStatement, columnIndex);

            assertThat(result).isEqualTo(testUuid);
        }

        @Test
        void shouldThrowExceptionForInvalidType() throws SQLException {
            int columnIndex = 1;
            doReturn(789).when(callableStatement).getObject(columnIndex);

            assertThatThrownBy(() -> typeHandler.getNullableResult(callableStatement, columnIndex))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Cannot convert class java.lang.Integer to UUID");
        }
    }
}