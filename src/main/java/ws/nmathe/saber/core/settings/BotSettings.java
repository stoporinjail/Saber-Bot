package ws.nmathe.saber.core.settings;

import java.io.*;
import java.util.Properties;

/**
 * contains configurable variables for the bot
 * file should be auto-generated with required fields if the file is found
 * not to exist.
 *
 * an optional 'webtoken' field may be added to sync stats with bots.discord.pw
 */
public class BotSettings
{
    private static final String FILENAME = "saber.properties";
    private static final String DEFAULT_TOKEN = "BOT_TOKEN";
    private static final String DEFAULT_ADMIN_ID = "ADMIN_USER_ID";
    private static final String DEFAULT_MAX_ENTRIES = "25";
    private static final String DEFAULT_COMMAND_PREFIX = "!";
    private static final String DEFAULT_ADMIN_COMMAND_PREFIX = "s.";

    private static final String DEFAULT_CONTROL_CHAN = "saber_control";
    private static final String DEFAULT_ANNOUNCE_CHAN = "general";

    private static final String DEFAULT_TIME_ZONE = "America/New_York";
    private static final String DEFAULT_CLOCK_FORMAT = "12";
    private static final String DEFAULT_ANNOUNCE_FORMAT = "Event %a: ``%t``";

    private Properties properties;

    public static BotSettings init()
    {
        BotSettings bc = new BotSettings();
        bc.properties = new Properties();
        InputStream input = null;

        try
        {
            input = new FileInputStream("./" + FILENAME);

            //load a properties file from class path, inside static method
            bc.properties.load(input);
        }
        catch (IOException ex)
        {
            generateFile();
            return null;
        }
        finally
        {
            if (input != null)
            {
                try
                {
                    input.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        return bc;
    }

    private static void generateFile()
    {
        Properties p = new Properties();
        OutputStream output = null;
        try
        {
            output = new FileOutputStream(FILENAME);

            // set the default values
            p.setProperty("token", DEFAULT_TOKEN);
            p.setProperty("admin_id", DEFAULT_ADMIN_ID);
            p.setProperty("max_entries", DEFAULT_MAX_ENTRIES);
            p.setProperty("command_prefix", DEFAULT_COMMAND_PREFIX);
            p.setProperty("admin_command_prefix", DEFAULT_ADMIN_COMMAND_PREFIX);
            p.setProperty("chan_control", DEFAULT_CONTROL_CHAN);
            p.setProperty("chan_announce", DEFAULT_ANNOUNCE_CHAN);
            p.setProperty("clock_format", DEFAULT_CLOCK_FORMAT);
            p.setProperty("time_zone", DEFAULT_TIME_ZONE);
            p.setProperty("announce_msg_format", DEFAULT_ANNOUNCE_FORMAT);

            // save properties to project root folder
            p.store(output, null);
        }
        catch (IOException io)
        {
            io.printStackTrace();
        }
        finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getToken()
    {
        return this.properties.getProperty("token");
    }

    public String getWebToken()
    {
        return this.properties.getProperty("webtoken");
    }

    public String getAdminId()
    {
        return this.properties.getProperty("admin_id");
    }

    public int getMaxEntries()
    {
        return Integer.parseInt(this.properties.getProperty("max_entries"));
    }

    public String getCommandPrefix()
    {
        return this.properties.getProperty("command_prefix");
    }

    public String getAdminPrefix()
    {
        return this.properties.getProperty("admin_command_prefix");
    }

    public String getAnnounceChan()
    {
        return this.properties.getProperty("chan_announce");
    }

    public String getControlChan()
    {
        return this.properties.getProperty("chan_control");
    }

    public String getAnnounceFormat()
    {
        return this.properties.getProperty("announce_msg_format");
    }

    public String getClockFormat()
    {
        return this.properties.getProperty("clock_format");
    }

    public String getTimeZone()
    {
        return this.properties.getProperty("time_zone");
    }
}
