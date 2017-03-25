package ws.nmathe.saber.core.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.__out;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

/**
 * Reads the next 7 days of events on a google calendar and converts
 * the events into a saber schedule entry. Events with recurrence are
 * condensed into one saber schedule entry.  Only daily and weekly by day
 * recurrence is supported.
 */
public class CalendarConverter
{
    /** Calendar service instance */
    private com.google.api.services.calendar.Calendar service;

    private static DateTimeFormatter rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void init()
    {
        // Build a new authorized API client service.
        // Note: Do not confuse this class with the
        //   com.google.api.services.calendar.model.Calendar class.
        try
        {
            service = GoogleAuth.getCalendarService();
            Main.getCommandHandler().putSync();
        }
        catch( IOException e )
        {
            __out.printOut(this.getClass(), e.getMessage());
        }
    }

    public boolean checkValidAddress( String address )
    {
        try
        {
            Events events = service.events().list(address)
                    .setMaxResults(1)
                    .execute();
            return true;
        }
        catch( IOException e )
        {
            return false;
        }

    }

    public void syncCalendar(String address, TextChannel channel)
    {
        Events events;

        try
        {
            ZonedDateTime min = ZonedDateTime.now();
            ZonedDateTime max = min.plusDays(7);

            events = service.events().list(address)
                    .setTimeMin(new DateTime(min.format(rfc3339Formatter)))
                    .setTimeMax(new DateTime(max.format(rfc3339Formatter)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
        }
        catch( IOException e )
        {
            e.printStackTrace();
            return;
        }

        // purge channel of all entries
        Main.getDBDriver().getEventCollection()
                .find(eq("channelId", channel.getId()))
                .forEach((Consumer<? super Document>) document ->
                {
                    ScheduleEntry entry = Main.getEntryManager().getEntry((Integer) document.get("_id"));
                    Message msg = entry.getMessageObject();
                    if( msg==null )
                        return;

                    Main.getEntryManager().removeEntry((Integer) document.get("_id"));
                    MessageUtilities.deleteMsg(msg, null);
                });

        // change the zone to match the calendar
        ZoneId zone = ZoneId.of( events.getTimeZone() );
        Main.getScheduleManager().setTimeZone( channel.getId(), zone );

        HashSet<String> uniqueEvents = new HashSet<>();

        // convert every entry and add it to the scheduleManager
        for(Event event : events.getItems())
        {
            ZonedDateTime start;
            ZonedDateTime end;
            String title;
            ArrayList<String> comments = new ArrayList<>();
            int repeat = 0;

            start = ZonedDateTime.parse(event.getStart().getDateTime().toStringRfc3339(), rfc3339Formatter).withZoneSameInstant(zone);
            end = ZonedDateTime.parse(event.getEnd().getDateTime().toStringRfc3339(), rfc3339Formatter).withZoneSameInstant(zone);

            if( event.getSummary() == null )
                title = "(No title)";
            else
                title = event.getSummary();

            if( event.getDescription() != null )
            {
                for( String comment : event.getDescription().split("\n") )
                {
                    if( !comment.trim().isEmpty() )
                        comments.add( comment );
                }
            }

            List<String> recurrence = event.getRecurrence();
            String recurrenceId = event.getRecurringEventId();
            if( recurrenceId != null )
            {
                try
                {
                    recurrence = service.events().get(address, recurrenceId).execute().getRecurrence();
                }
                catch( IOException e )
                {
                    e.printStackTrace();
                }
            }
            if( recurrence != null )
            {
                for( String rule : recurrence )
                {
                    if( rule.startsWith("RRULE") && rule.contains("FREQ" ) )
                    {
                        String tmp = rule.split("FREQ=")[1].split(";")[0];
                        if( tmp.equals("DAILY" ) )
                            repeat = 0b1111111;
                        else if( tmp.equals("WEEKLY") && rule.contains("BYDAY") )
                        {
                            tmp = rule.split("BYDAY=")[1].split(";")[0];
                            repeat = ParsingUtilities.parseWeeklyRepeat(tmp);
                        }
                    }
                }
            }

            if(!uniqueEvents.contains(recurrenceId==null ? event.getId() : recurrenceId))
            {
                Main.getEntryManager().newEntry(title, start, end, comments, repeat, event.getHtmlLink(), channel);

                uniqueEvents.add(recurrenceId==null ? event.getId() : recurrenceId);
            }
        }
    }
}
