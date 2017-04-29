package ws.nmathe.saber.commands.general;

import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Command which is used to adjust the schedule settings for a channel
 */
public class ConfigCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "config";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <channel> [<option> <new config>]```\n" +
                "The config command can be used to both view and " +
                "change schedule settings. To view a schedule's current settings, supply only the ``<channel>`` argument.\n" +
                "Options are 'msg' (announcement message format), chan (announcement channel), zone (timezone to use), and clock " +
                "('12' to use am/pm or '24' for full form)." +
                "\n\n" +
                "To turn off calendar sync or event reminders, pass **off** as a command parameter when setting the config option." +
                "\n\n```diff\n+ Event Reminders```\n" +
                "Events can be configured to send reminder announcements at configured thresholds before an event begins.\n" +
                "To configure the times at which events on the schedule should send reminders, use the 'remind' with an " +
                "argument containing the relative times to remind delimited by spaces (see examples).\n" +
                "Reminder messages are defined by a configured format, see below." +
                "\n\n```diff\n+ Custom announcements and reminders```\n" +
                "When an event begins or ends an announcement message is sent to the configured channel.\n" +
                "The message that is sent is determined from the message format the schedule is configured to use." +
                "\n\n" +
                "When creating a custom announcement message format the " +
                "'%' acts as a delimiter for entry parameters such as the title or a comment.\n" +
                "**%t** will cause the entry title to be inserted\n**%c[1-9]** will cause the nth comment to be inserted\n**%a** will insert" +
                " 'begins' or 'ends'\n**%%** will insert %." +
                "\n\n" +
                "If you wish to create a multi-line message like the default message format, new lines can be entered using" +
                " SHIFT+Enter. However, be sure to encapsulate the entire string (new lines included) in quotations.";

        String USAGE_BRIEF = "``" + invoke + "`` - configure a schedule's settings";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " #schedule``\n" +
                "``" + invoke + " #guild_events msg \"@here The event %t %a. %c1\"``\n" +
                "``" + invoke + " #guild_events remind \"10, 20, 30 min\"``\n" +
                "``" + invoke + " #events_channel chan \"general\"``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 1)
            return "That's not enough arguments! Use ``" + invoke + " <channel> [<option> <new config>]``";

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + invoke + "`` command to create a new schedule!";

        if(Main.getScheduleManager().isLocked(cId))
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes. " +
                    "(If this does not go away ping @notem in the support server)";

        index++;

        if (args.length > 1)
        {
            if (args.length < 3)
                return "That's not enough arguments! Use ``" + invoke + " <channel> [<option> <new config>]``";

            switch( args[index++] )
            {
                case "m":
                case "msg":
                case "message":
                    break;

                case "ch":
                case "chan":
                case "channel":
                    break;

                case "z":
                case "zone":
                    try
                    {
                        ZoneId.of(args[index]);
                    } catch(Exception e)
                    {
                        return "**" + args[index] +  "** is not a valid timezone! Use the ``zones`` command to learn " +
                                "what options are available.";
                    }
                    break;

                case "cl":
                case "clock":
                    if( !args[index].equals("24") && !args[index].equals("12"))
                        return "Argument **" + args[index] +  "** is not a valid option. Argument must be **24** " +
                                "or **12**";
                    break;

                case "s":
                case "sync":
                    if( args[index].equals("off") )
                        return "";
                    if( !Main.getCalendarConverter().checkValidAddress(args[index]) )
                        return "I cannot sync to **" + args[index] + "**! Provide a valid google calendar url or **off**.";
                    break;

                case "t":
                case "time":
                    if(!VerifyUtilities.verifyTime(args[index]))
                        return "I cannot parse ``" + args[index] + "`` into a time!";
                    break;

                case "r":
                case "remind":
                case "reminder":
                case "reminders":
                    if(args[index].toLowerCase().equals("off"))
                        return "";

                    List<Integer> list = ParsingUtilities.parseReminderStr(args[index]);
                    if (list.size() <= 0)
                        return "I could not parse out any times!";
                    if (list.size() > 10)
                        return "More than 10 reminders are not allowed!";
                    for(Integer i : list)
                    {
                        if (i<5)
                            return "Reminders under 5 minutes are not allowed!";
                    }
                    break;

                case "rm":
                case "remind-msg":
                    break;

                case "rch":
                case "remind-chan":
                    break;

                default:
                    return "Argument **" + args[index-1] + "** is not a configurable setting! Options are **msg**, " +
                            "**chan**, **zone**, **clock**, **sync**, **time**, and **remind**.";
            }
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        String cId = args[index].replace("<#","").replace(">","");
        TextChannel scheduleChan = event.getGuild()
                .getTextChannelById(args[index].replace("<#","").replace(">",""));

        index++;

        if (args.length > 1)
        {
            switch (args[index++])
            {
                case "m":
                case "msg":
                    Main.getScheduleManager().setAnnounceFormat(scheduleChan.getId(), args[index]);
                    MessageUtilities.sendMsg(this.genMsgStr(cId, 1), event.getChannel(), null);
                    break;

                case "ch":
                case "chan":
                    TextChannel tmp = event.getGuild()
                            .getTextChannelById(args[index].replace("<#","").replace(">",""));
                    String chanName = (tmp==null) ? args[index] : tmp.getName();

                    Main.getScheduleManager().setAnnounceChan(scheduleChan.getId(), chanName);

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 1), event.getChannel(), null);
                    break;

                case "z":
                case "zone":
                    ZoneId zone = ZoneId.of(args[index]);
                    Main.getScheduleManager().setTimeZone(scheduleChan.getId(), zone);

                    // update schedule entries with new timezone
                    Main.getDBDriver().getEventCollection()
                            .updateMany(eq("channelId", scheduleChan.getId()), set("zone",zone.toString()));

                    // reload the schedule display
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                                    Main.getEntryManager().reloadEntry((Integer) document.get("_id")));

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "cl":
                case "clock":
                    Main.getScheduleManager().setClockFormat(scheduleChan.getId(), args[index]);

                    // reload the schedule display
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                                    Main.getEntryManager().reloadEntry((Integer) document.get("_id")));

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "s":
                case "sync":
                    if( Main.getCalendarConverter().checkValidAddress(args[index]) )
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), args[index]);
                    else
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), "off");

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 3), event.getChannel(), null);
                    break;

                case "t":
                case "time":
                    ZonedDateTime syncTime = ParsingUtilities.parseTime(
                            ZonedDateTime.now().withZoneSameLocal(Main.getScheduleManager().getTimeZone(cId)),
                            args[index]
                    );

                    // don't allow times set in the past
                    if(syncTime.isBefore(ZonedDateTime.now()))
                        syncTime.plusDays(1);

                    Main.getScheduleManager().setSyncTime(cId, Date.from(syncTime.toInstant()));

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 4), event.getChannel(), null);
                    break;

                case "r":
                case "remind":
                    List<Integer> rem;
                    if(args[index].toLowerCase().equals("off"))
                        rem = new ArrayList<>();
                    else
                        rem = ParsingUtilities.parseReminderStr(args[index]);

                    Main.getScheduleManager().setDefaultReminders(cId, rem);

                    // for every entry on channel, update
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                            {
                                // generate new entry reminders
                                List<Date> reminders = new ArrayList<>();
                                Instant start = ((Date) document.get("start")).toInstant();
                                for(Integer til : rem)
                                {
                                    if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
                                    {
                                        reminders.add(Date.from(start.minusSeconds(til*60)));
                                    }
                                }

                                // update db
                                Main.getDBDriver().getEventCollection()
                                        .updateOne(eq("_id", document.get("_id")), set("reminders", reminders));

                                // reload displayed message
                                Main.getEntryManager().reloadEntry((Integer) document.get("_id"));
                            });

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "rm":
                case "remind-msg":
                    Main.getScheduleManager().setReminderFormat(scheduleChan.getId(), args[index]);

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "rc":
                case "remind-chan":
                    TextChannel tmp2 = event.getGuild().getTextChannelById(args[index].replace("<#","").replace(">",""));
                    String chanName2 = (tmp2==null) ? args[index] : tmp2.getName();

                    Main.getScheduleManager().setReminderChan(scheduleChan.getId(), chanName2);

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;
            }
        }
        else    // print out all settings
        {
            MessageUtilities.sendMsg(this.genMsgStr(cId, 0), event.getChannel(), null);
        }
    }

    private String genMsgStr(String cId, int type)
    {
        ZoneId zone = Main.getScheduleManager().getTimeZone(cId);
        String content = "**Configuration for** <#" + cId + ">\n";

        switch(type)
        {
            default:
            case 1:
                content += "```js\n" +
                        "// Event Announcement Settings\n" +
                        "[msg] Format for start/end messages\n " + "\"" +
                        Main.getScheduleManager().getAnnounceFormat(cId).replace("```","`\uFEFF`\uFEFF`") + "\"\n" +
                        "\n[chan] Announce start/end to channel\n " +
                        "\"" + Main.getScheduleManager().getAnnounceChan(cId) + "\"\n" +
                        "```";

                if(type == 1)
                    break;
            case 2:
                List<Integer> reminders = Main.getScheduleManager().getDefaultReminders(cId);
                String reminderStr = "";
                if(reminders.isEmpty())
                {
                    reminderStr = "off";
                } else
                {
                    reminderStr += reminders.get(0);
                    for (int i=1; i<reminders.size()-1; i++)
                    {
                        reminderStr += ", " + reminders.get(i) ;
                    }
                    if(reminders.size() > 1)
                        reminderStr += " and " + reminders.get(reminders.size()-1);
                    reminderStr += " minutes";
                }

                content += "```js\n" +
                        "// Event Reminder Settings\n" +
                        "[remind] Send reminders before event begins\n " +
                        "\"" + reminderStr + "\"\n" +
                        "\n[remind-msg] Format for reminder messages\n " + "\"" +
                        Main.getScheduleManager().getReminderFormat(cId).replace("```","`\uFEFF`\uFEFF`") + "\"\n" +
                        "\n[remind-chan] Announce reminder to channel\n " +
                        "\"" + Main.getScheduleManager().getReminderChan(cId) + "\"\n" +
                        "```";

                if(type == 2)
                    break;
            case 3:
                content += "```js\n" +
                        "// Event Display Settings\n" +
                        "[zone] Display events in this timezone\n " +
                        "\"" + zone + "\"\n" +
                        "\n[clock] Display events using this clock format\n " +
                        "\"" + Main.getScheduleManager().getClockFormat(cId) + "\"\n" +
                        "```";

                if(type == 3)
                    break;
            case 4:
                Date syncTime = Main.getScheduleManager().getSyncTime(cId);
                OffsetTime sync_time_display = ZonedDateTime.ofInstant(syncTime.toInstant(), zone)
                        .toOffsetDateTime().toOffsetTime().truncatedTo(ChronoUnit.MINUTES);

                content += "```js\n" +
                        "// Schedule Sync Settings\n" +
                        "[sync] Sync to Google calendar address\n " +
                        "\"" + Main.getScheduleManager().getAddress(cId) + "\"\n" +
                        "\n[time] Time of day to sync calendar\n " +
                        "\"" + sync_time_display + "\"\n" +
                        "```";

                if(type == 4)
                    break;
        }

        return content;
    }
}
