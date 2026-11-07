package com.cardwallet;

import com.cardwallet.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/** Shared MockMvc setup + an isolated temp SQLite database reset before every test. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractTest {

    @Autowired
    protected MockMvc mockMvc;

    @BeforeEach
    void resetDatabase() throws IOException, SQLException {
        Path tempDb = Files.createTempFile("cardwallet-test", ".db");
        tempDb.toFile().deleteOnExit();
        System.setProperty("cardvault.db", tempDb.toString());
        Database.initDb(true);
    }
}
