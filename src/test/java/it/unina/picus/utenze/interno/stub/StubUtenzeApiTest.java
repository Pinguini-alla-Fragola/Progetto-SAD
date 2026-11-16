package it.unina.picus.utenze.interno.stub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Il limite di 72 byte UTF-8 dello stub coincide con quello di UtenzeService. */
class StubUtenzeApiTest {

    private static StubUtenzeApi stubCon(String password) {
        return new StubUtenzeApi(new BCryptPasswordEncoder(4), password);
    }

    @Test
    void passwordDi72ByteAccettata() {
        String password = "a".repeat(StubUtenzeApi.LUNGHEZZA_MAX_PASSWORD_BYTE);

        assertThat(stubCon(password).verificaCredenziali(StubUtenzeApi.BANCO, password)).isPresent();
    }

    @Test
    void passwordDi72ByteConSuffissoExtraRifiutata() {
        String valida = "a".repeat(StubUtenzeApi.LUNGHEZZA_MAX_PASSWORD_BYTE);

        assertThat(stubCon(valida).verificaCredenziali(StubUtenzeApi.BANCO, valida + "b")).isEmpty();
    }

    @Test
    void ilLimiteSiMisuraInByteUtf8NonInCaratteri() {
        String multibyte = "è".repeat(36); // 36 caratteri, 72 byte
        StubUtenzeApi stub = stubCon(multibyte);

        assertThat(stub.verificaCredenziali(StubUtenzeApi.BANCO, multibyte)).isPresent();
        assertThat(stub.verificaCredenziali(StubUtenzeApi.BANCO, multibyte + "x")).isEmpty();
        assertThat(stub.verificaCredenziali(StubUtenzeApi.BANCO, "è".repeat(37))).isEmpty();
    }
}
