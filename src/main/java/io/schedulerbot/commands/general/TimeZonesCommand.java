package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;
import java.util.Set;

/**
 */
public class TimeZonesCommand implements Command
{

    @Override
    public String help(boolean brief)
    {
        String invoke = Main.getSettings().getCommandPrefix() + "timezone";

        String USAGE_EXTENDED = "The raw output can be overwhelming. Output can be filtered by providing" +
                "one argument to the command to filter for all zones which contain the word provided. For both our sakes," +
                " always provide a filter.";

        String USAGE_BRIEF = "**" + invoke + "** - shows all available timezones.";

        String EXAMPLES = "Ex1: **" + invoke + " america**";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return args.length<2;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Set<String> zones = ZoneId.getAvailableZoneIds();
        if( args.length == 0 )
        {
            String msg = "**Available options for time zones**\n";
            for (String zone : zones )
            {
                if (msg.length() > 1900)
                {
                    MessageUtilities.sendMsg(msg, event.getChannel(), null);
                    msg = "**continued. . .**\n";
                }
                msg += "  " + zone + "\n";
            }

            MessageUtilities.sendMsg(msg, event.getChannel(), null);
        }
        else
        {
            String msg = "**Time zones for " + args[0] + "**\n";
            for (String zone : zones )
            {
                if (msg.length() > 1900)
                {
                    MessageUtilities.sendMsg(msg, event.getChannel(), null);
                    msg = "\n";
                }
                if( zone.toUpperCase().contains(args[0].toUpperCase()) )
                {
                    msg += "  " + zone + "\n";
                }
            }

            MessageUtilities.sendMsg(msg, event.getChannel(), null);
        }
    }
}