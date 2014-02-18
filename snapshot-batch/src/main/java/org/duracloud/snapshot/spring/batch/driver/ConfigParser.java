/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.driver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.duracloud.snapshot.spring.batch.config.DatabaseConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotNotifyConfig;

import java.io.File;

/**
 * @author Erik Paulsson Date: 2/7/14
 */
public class ConfigParser {

    protected static final int DEFAULT_PORT = 443;
    protected static final String DEFAULT_CONTEXT = "durastore";

    private Options cmdOptions;

    public ConfigParser() {
        // Command Line Options
        cmdOptions = new Options();

        Option hostOption =
            new Option("h", "host", true, "the host address of the DuraCloud "
                + "DuraStore application");
        hostOption.setRequired(true);
        cmdOptions.addOption(hostOption);

        Option portOption =
            new Option("r",
                       "port",
                       true,
                       "the port of the DuraCloud DuraStore application "
                           + "(optional, default value is " + DEFAULT_PORT
                           + ")");
        portOption.setRequired(false);
        cmdOptions.addOption(portOption);

        Option usernameOption =
            new Option("u",
                       "username",
                       true,
                       "the username necessary to perform writes to DuraStore");
        usernameOption.setRequired(true);
        cmdOptions.addOption(usernameOption);

        Option passwordOption =
            new Option("p",
                       "password",
                       true,
                       "the password necessary to perform writes to DuraStore.");
        passwordOption.setRequired(true);
        cmdOptions.addOption(passwordOption);

        Option storeIdOption =
            new Option("i",
                       "store-id",
                       true,
                       "the Store ID for the DuraCloud storage provider");
        storeIdOption.setRequired(false);
        cmdOptions.addOption(storeIdOption);

        Option sanpshotIdOption =
            new Option("n",
                       "snapshot-id",
                       true,
                       "the Store ID for the DuraCloud storage provider");
        sanpshotIdOption.setRequired(true);
        cmdOptions.addOption(sanpshotIdOption);

        Option space = new Option("s", "space", true, "the space to snapshot");
        space.setRequired(true);
        cmdOptions.addOption(space);

        Option contentDirOption =
            new Option("c",
                       "content-dir",
                       true,
                       "retrieved content is stored in this local directory");
        contentDirOption.setRequired(true);
        cmdOptions.addOption(contentDirOption);

        Option workDirOption =
            new Option("w",
                       "work-dir",
                       true,
                       "logs and output files will be stored in the work "
                           + "directory");
        workDirOption.setRequired(true);
        cmdOptions.addOption(workDirOption);

        // database options
        Option databaseURL =
            new Option("dj",
                       "database-jdbc-url",
                       true,
                       "jdbc connection for the database");
        databaseURL.setRequired(true);
        cmdOptions.addOption(databaseURL);

        Option databaseUsername =
            new Option("du", "database-username", true, "database username");
        databaseUsername.setRequired(true);
        cmdOptions.addOption(databaseUsername);

        Option databasePassword =
            new Option("dp", "database-password", true, "database password");
        databasePassword.setRequired(true);
        cmdOptions.addOption(databasePassword);

        Option clean =
            new Option("C", "clean", false, "Wipe clean database, content, and"
                + " work directories on startup.");
        cmdOptions.addOption(clean);

        // notification options
        Option awsUsername =
            new Option("aa", "aws-access-key", true,
                       "An AWS Access Key that allows calls to be made to " +
                       "AWS services");
        awsUsername.setRequired(true);
        cmdOptions.addOption(awsUsername);

        Option awsPassword =
            new Option("as", "aws-secret-key", true,
                       "An AWS Secret Key that allows calls to be made to " +
                       "AWS services");
        awsPassword.setRequired(true);
        cmdOptions.addOption(awsPassword);

        Option originatorEmail =
            new Option("eo", "email-originator", true,
                       "The email address used in the TO field of email " +
                       "notifications");
        originatorEmail.setRequired(true);
        cmdOptions.addOption(originatorEmail);

        Option duracloudEmail =
            new Option("edc", "email-duracloud", true,
                       "The email address used to provide information to " +
                       "the DuraCloud team");
        duracloudEmail.setRequired(true);
        cmdOptions.addOption(duracloudEmail);

        Option dpnEmail =
            new Option("edn", "email-dpn", true,
                       "The email address used to provide information to " +
                       "the DPN team");
        dpnEmail.setRequired(true);
        cmdOptions.addOption(dpnEmail);
    }

    protected SnapshotConfig processSnapshotConfigOptions(String[] args)
        throws ParseException {
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(cmdOptions, args);
        SnapshotConfig config = new SnapshotConfig();

        config.setContext(DEFAULT_CONTEXT);
        config.setHost(cmd.getOptionValue("h"));

        if (cmd.hasOption("r")) {
            try {
                config.setPort(Integer.valueOf(cmd.getOptionValue("r")));
            } catch (NumberFormatException e) {
                throw new ParseException("The value for port (-r) must be "
                    + "a number.");
            }
        } else {
            config.setPort(DEFAULT_PORT);
        }

        config.setSpace(cmd.getOptionValue("s"));
        config.setStoreId(cmd.getOptionValue("i"));
        config.setSnapshotId(cmd.getOptionValue("n"));
        config.setContentDir(new File(cmd.getOptionValue("c")));

        return config;
    }

    /**
     * Parses command line configuration into an SnapshotConfig structure,
     * validates correct values along the way.
     * 
     * Prints a help message and exits the JVM on parse failure.
     * 
     * @param args command line configuration values
     * @return populated SnapshotConfig
     */
    public SnapshotConfig processSnapshotConfigCommandLine(String[] args) {
        SnapshotConfig config = null;
        try {
            config = processSnapshotConfigOptions(args);
        } catch (ParseException e) {
            printHelp(e.getMessage());
        }
        return config;
    }

    /**
     * Parses command line configuration into an DatabaseConfig structure,
     * validates correct values along the way.
     * 
     * Prints a help message and exits the JVM on parse failure.
     * 
     * @param args command line configuration values
     * @return populated DatabaseConfig
     */
    public DatabaseConfig processDBCommandLine(String[] args) {
        DatabaseConfig config = null;
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(cmdOptions, args);
            config = new DatabaseConfig();
            config.setUrl(cmd.getOptionValue("dj"));
            config.setUsername(cmd.getOptionValue("du"));
            config.setPassword(cmd.getOptionValue("dp"));
            if (cmd.hasOption("C"))
                config.setClean(true);
            return config;
        } catch (ParseException e) {
            printHelp(e.getMessage());
        }
        return config;
    }

    /**
     * Parses command line configuration into an SnapshotNotifyConfig structure.
     *
     * Prints a help message and exits the JVM on parse failure.
     *
     * @param args command line configuration values
     * @return populated SnapshotNotifyConfig
     */
    public SnapshotNotifyConfig processNotifyCommandLine(String[] args) {
        SnapshotNotifyConfig config = null;
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(cmdOptions, args);

            config = new SnapshotNotifyConfig();
            config.setSesUsername(cmd.getOptionValue("aa"));
            config.setSesPassword(cmd.getOptionValue("as"));
            config.setOriginatorEmailAddress(cmd.getOptionValue("eo"));

            String[] duracloudEmails = new String[1];
            duracloudEmails[0] = cmd.getOptionValue("edc");
            config.setDuracloudEmailAddresses(duracloudEmails);

            String[] dpnEmails = new String[1];
            dpnEmails[0] = cmd.getOptionValue("edn");
            config.setDpnEmailAddresses(dpnEmails);
        } catch (ParseException e) {
            printHelp(e.getMessage());
        }
        return config;
    }

    /**
     * Parses command line configuration into an DuracloudConfig structure.
     *
     * Prints a help message and exits the JVM on parse failure.
     *
     * @param args command line configuration values
     * @return populated DuracloudConfig
     */
    public SnapshotJobManagerConfig processDuracloudCommandLine(String[] args) {
        SnapshotJobManagerConfig config = null;
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(cmdOptions, args);
            config = new SnapshotJobManagerConfig();
            config.setDuracloudUsername(cmd.getOptionValue("u"));
            config.setDuracloudPassword(cmd.getOptionValue("p"));
            config.setWorkDir(new File(cmd.getOptionValue("w")));
        } catch (ParseException e) {
            printHelp(e.getMessage());
        }
        return config;
    }

    private void printHelp(String message) {
        System.out.println("\n-----------------------\n"
            + message + "\n-----------------------\n");

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Snapshot Tool", cmdOptions);
        System.exit(1);
    }

}
