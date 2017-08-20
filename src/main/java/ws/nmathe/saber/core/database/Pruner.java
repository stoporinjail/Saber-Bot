package ws.nmathe.saber.core.database;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.where;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

/**
 */
public class Pruner implements Runnable
{
    @Override
    public void run()
    {
        Logging.info(this.getClass(), "Running database pruner. . .");

        // purge guild setting entries for any guild not connected to the bot
        Bson query = new Document();
        Main.getDBDriver().getGuildCollection().find(query)
                .projection(fields(include("_id")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        // identify which shard is responsible for the schedule
                        String guildId = document.getString("_id");
                        JDA jda = Main.getShardManager().getShard(guildId);

                        // if the shard is not connected, do not prune
                        if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                        Guild guild = jda.getGuildById(guildId);
                        if(guild == null)
                        {
                            Main.getDBDriver().getGuildCollection().deleteOne(eq("_id", guildId));
                            Main.getDBDriver().getEventCollection().deleteMany(eq("guildId", guildId));
                            Main.getDBDriver().getScheduleCollection().deleteMany(eq("guildId", guildId));
                            Logging.info(this.getClass(), "Pruned guild with ID: " + guildId);
                        }
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                });

        // purge schedule entries that the bot cannot connect to
        query = new Document();
        Main.getDBDriver().getScheduleCollection().find(query)
                .projection(fields(include("_id")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        // identify which shard is responsible for the schedule
                        String guildId = document.getString("guildId");
                        JDA jda = Main.getShardManager().getShard(guildId);

                        // if the shard is not connected, do not prune
                        if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                        String chanId = document.getString("_id");
                        MessageChannel channel = jda.getTextChannelById(chanId);
                        if(channel == null)
                        {
                            Main.getDBDriver().getEventCollection().deleteMany(eq("channeldId", chanId));
                            Main.getDBDriver().getScheduleCollection().deleteMany(eq("_id", chanId));
                            Logging.info(this.getClass(), "Pruned schedule with channel ID: " + chanId);
                        }
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                });


        // purge events for which the bot cannot access the message
        query = new Document();
        Main.getDBDriver().getEventCollection().find(query)
                .projection(fields(include("_id", "messageId", "channelId", "guildId")))
                .forEach((Consumer<? super Document>) document ->
                {
                    try
                    {
                        // identify which shard is responsible for the schedule
                        String guildId = document.getString("guildId");
                        JDA jda = Main.getShardManager().getShard(guildId);

                        // if the shard is not connected, do not prune
                        if(JDA.Status.valueOf("CONNECTED") != jda.getStatus()) return;

                        // validate message id
                        Integer eventId = document.getInteger("_id");
                        String messageId = document.getString("messageId");
                        if(messageId == null)
                        {
                            Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                            Logging.info(this.getClass(), "Pruned event with ID: " + eventId);
                            return;
                        }

                        // validate channel id
                        String channelId = document.getString("channelId");
                        MessageChannel channel = jda.getTextChannelById(channelId);
                        if(channel==null)
                        {
                            Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                            Logging.info(this.getClass(), "Pruned event with ID: " + eventId);
                            return;
                        }

                        // validate message is accessible
                        channel.getMessageById(messageId).queue(
                                message ->
                                {
                                    if(message == null)
                                    {
                                        Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                                        Logging.info(this.getClass(), "Pruned event with ID: " + eventId + " on channel with ID: " + channelId);
                                    }
                                },
                                throwable ->
                                {
                                    Main.getDBDriver().getEventCollection().deleteOne(eq("_id", eventId));
                                    Logging.info(this.getClass(), "Pruned event with ID: " + eventId + " on channel with ID: " + channelId);
                                });
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
            });
    }
}
