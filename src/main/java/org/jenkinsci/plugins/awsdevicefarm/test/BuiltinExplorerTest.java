package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for a built-in Explorer test.
 */
public class BuiltinExplorerTest {

    private final String username;
    private final String password;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String username;
        private String password;

        /**
         * username setter
         * @param username username to use if explorer encounters a login form.
         * @return the Builder object.
         */
        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * password setter password to use if explorer encounters a login form.
         * @param password the Builder object.
         * @return
         */
        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * Builder method.
         * @return The new POJO.
         */
        public BuiltinExplorerTest build() {
            return new BuiltinExplorerTest(this);
        }

    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private BuiltinExplorerTest(Builder builder) {
        this.username = builder.username;
        this.password = builder.password;
    }

    public BuiltinExplorerTest(String username, String password) {
        super();
        this.username = username;
        this.password = password;
    }

    /**
     * username getter
     * @return username to use in the login form
     */
    public String getUsername() {
        return username;
    }

    /**
     * password getter
     * @return password to use in the login form
     */
    public String getPassword() {
        return password;
    }

}
