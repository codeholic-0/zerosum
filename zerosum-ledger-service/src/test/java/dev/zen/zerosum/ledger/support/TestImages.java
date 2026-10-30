package dev.zen.zerosum.ledger.support;

import org.testcontainers.utility.DockerImageName;

public class TestImages {
    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:17");

    private TestImages() {}
}
