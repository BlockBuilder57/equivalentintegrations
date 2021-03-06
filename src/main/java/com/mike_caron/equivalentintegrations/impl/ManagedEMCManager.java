package com.mike_caron.equivalentintegrations.impl;

import com.mike_caron.equivalentintegrations.EquivalentIntegrationsMod;
import com.mike_caron.equivalentintegrations.OfflineEMCWorldData;
import com.mike_caron.equivalentintegrations.api.events.EMCChangedEvent;
import com.mike_caron.equivalentintegrations.storage.EMCInventory;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Mod.EventBusSubscriber
public class ManagedEMCManager
{
    private static int TICK_DELAY = 60;
    private static int EMC_CHECK_DELAY = 5;

    private World world;

    private final HashMap<UUID, EMCInventory> emcInventories = new HashMap<>();

    private HashMap<UUID, Integer> dirtyPlayers = new HashMap<>();
    private HashMap<UUID, Double> lastKnownEmc = new HashMap<>();
    //private HashMap<UUID, IKnowledgeProvider> knowledgeProviders = new HashMap<>();
    private HashSet<UUID> updateEmc = new HashSet<>();

    //private final HashMap<ItemStack, Long> emcValues = new HashMap<>();
    private final Lock lock = new ReentrantLock();
    //private final HashMap<ItemStack, Boolean> cacheBlacklist = new HashMap<>();

    private ITransmutationProxy transmutationProxy;
    private IEMCProxy emcProxy;

    private int emcCheckTimer = 0;

    public ManagedEMCManager(World world)
    {
        this.world = world;

        transmutationProxy = ProjectEAPI.getTransmutationProxy();
        emcProxy = ProjectEAPI.getEMCProxy();
    }

    public double getEMC(UUID owner)
    {
        double ret = -1D;

        EntityPlayerMP player = getEntityPlayerMP(owner);

        if(player == null && OfflineEMCWorldData.get(world).hasCachedEMC(owner))
        {
            //EquivalentIntegrationsMod.logger.debug("Retrieving cached EMC value for {}", owner);
            ret = OfflineEMCWorldData.get(world).getCachedEMC(owner);
        }

        if(ret == -1D)
        {
            //EquivalentIntegrationsMod.logger.debug("Retrieving live EMC value for {}", owner);
            IKnowledgeProvider knowledge = getKnowledgeProvider(owner);
            ret = knowledge.getEmc();
        }

        if(!lastKnownEmc.containsKey(owner))
        {
            lastKnownEmc.put(owner, 0D);
        }

        if(lastKnownEmc.get(owner) != ret)
        {
            lastKnownEmc.put(owner, ret);
            //MinecraftForge.EVENT_BUS.post(new EMCChangedEvent(owner, ret));
            updateEmc.add(owner);
        }

        return ret;
    }

    private IKnowledgeProvider getKnowledgeProvider(UUID owner)
    {
        IKnowledgeProvider knowledge;
        //if(knowledgeProviders.containsKey(owner))
        //{
        //    knowledge = knowledgeProviders.get(owner);
        //}
        //else
        //{
            knowledge = transmutationProxy.getKnowledgeProviderFor(owner);
        //    knowledgeProviders.put(owner, knowledge);
        //}
        return knowledge;
    }

    public void setEMC(UUID owner, double emc)
    {
        lock.lock();
        try
        {
            double currentEmc = getEMC(owner);
            if (emc != currentEmc)
            {
                EntityPlayerMP player = getEntityPlayerMP(owner);

                if (player != null)
                {
                    IKnowledgeProvider knowledge = getKnowledgeProvider(owner);
                    knowledge.setEmc(emc);
                    markDirty(owner);
                }
                else
                {
                    OfflineEMCWorldData.get(world).setCachedEMC(owner, emc);
                }

                lastKnownEmc.put(owner, emc);
                //MinecraftForge.EVENT_BUS.post(new EMCChangedEvent(owner, emc));
                updateEmc.add(owner);
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public long withdrawEMC(UUID owner, long amt)
    {
        lock.lock();
        try
        {
            double currentEmc = getEMC(owner);
            if (amt > currentEmc)
            {
                amt = (long) currentEmc;
            }

            double newEmc = currentEmc - amt;

            if (newEmc != currentEmc)
            {
                EntityPlayerMP player = getEntityPlayerMP(owner);

                if (player != null)
                {
                    IKnowledgeProvider knowledge = getKnowledgeProvider(owner);
                    knowledge.setEmc(newEmc);
                    markDirty(owner);
                }
                else
                {
                    OfflineEMCWorldData.get(world).setCachedEMC(owner, newEmc);
                }

                lastKnownEmc.put(owner, newEmc);
                //MinecraftForge.EVENT_BUS.post(new EMCChangedEvent(owner, newEmc));
                updateEmc.add(owner);
            }

            return amt;
        }
        finally
        {
            lock.unlock();
        }
    }

    public void depositEMC(UUID owner, long amt)
    {
        lock.lock();
        try
        {
            double currentEmc = getEMC(owner);

            double newEmc = currentEmc + amt;

            if (newEmc != currentEmc)
            {
                EntityPlayerMP player = getEntityPlayerMP(owner);

                if (player != null)
                {
                    IKnowledgeProvider knowledge = getKnowledgeProvider(owner);
                    knowledge.setEmc(newEmc);
                    markDirty(owner);
                }
                else
                {
                    OfflineEMCWorldData.get(world).setCachedEMC(owner, newEmc);
                }

                lastKnownEmc.put(owner, newEmc);
                //MinecraftForge.EVENT_BUS.post(new EMCChangedEvent(owner, newEmc));
                updateEmc.add(owner);
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void tick()
    {
        lock.lock();

        try
        {
            Iterator<UUID> keys = dirtyPlayers.keySet().iterator();
            while(keys.hasNext())
            {
                UUID player = keys.next();
                int ticks = dirtyPlayers.get(player);
                ticks--;

                if (ticks <= 0)
                {
                    keys.remove();

                    EntityPlayerMP playermp = getEntityPlayerMP(player);

                    if (playermp == null)
                    {
                        //they went offline... no problem
                    }
                    else
                    {
                        IKnowledgeProvider knowledge = getKnowledgeProvider(player);
                        knowledge.sync(playermp);
                    }
                }
                else
                {
                    dirtyPlayers.put(player, ticks);
                }
            }

            if (--emcCheckTimer <= 0)
            {
                emcCheckTimer = EMC_CHECK_DELAY;

                for (UUID player : lastKnownEmc.keySet())
                {
                    getEMC(player); //the event will be fired from within
                }
            }

            for (UUID player : updateEmc)
            {
                double emc = lastKnownEmc.get(player);

                MinecraftForge.EVENT_BUS.post(new EMCChangedEvent(player, emc));
            }

            updateEmc.clear();
        }
        finally
        {
            lock.unlock();
        }
    }

    public void playerLoggedIn(UUID owner)
    {
        lock.lock();
        try
        {
            OfflineEMCWorldData data = OfflineEMCWorldData.get(FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld());
            if (data.hasCachedEMC(owner))
            {
                IKnowledgeProvider knowledge = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(owner);
                knowledge.setEmc(data.getCachedEMC(owner));
                data.clearCachedEMC(owner);

                EntityPlayerMP player = getEntityPlayerMP(owner);
                knowledge.sync(player);
            }
            //knowledgeProviders.remove(owner);
        }
        finally
        {
            lock.lock();
        }
    }

    public long getEmcValue(ItemStack stack)
    {
        return EMCHelper.getEmcValue(stack);
        /*
        lock.lock();
        try
        {
            if(!cacheBlacklist.containsKey(stack.getItem()))
            {
                cacheBlacklist.put(stack.getItem(), stack.getMaxDamage() > 0);
            }

            if(cacheBlacklist.get(stack.getItem()))
            {
                return EMCHelper.getEmcValue(stack);
            }

            if (!emcValues.containsKey(stack.getItem()))
            {
                long value = EMCHelper.getEmcValue(stack);
                emcValues.put(stack.getItem(), value);
            }
            return emcValues.get(stack.getItem());
        }
        finally
        {
            lock.unlock();
        }
        */
    }

    public long getEmcSellValue(ItemStack stack)
    {
        return EMCHelper.getEmcSellValue(stack);
        /*ItemStack idealStack = getIdeal(stack);
        lock.lock();

        try
        {
            if(!cacheBlacklist.containsKey(idealStack))
            {
                cacheBlacklist.put(idealStack, idealStack.getMaxDamage() > 0);
            }

            if(cacheBlacklist.get(idealStack))
            {
                return EMCHelper.getEmcSellValue(stack);
            }

            if (!emcValues.containsKey(idealStack))
            {
                long value = EMCHelper.getEmcValue(idealStack);
                emcValues.put(idealStack, value);
            }


            return (long) (emcValues.get(stack) * EMCMapper.covalenceLoss);
        }
        finally
        {
            lock.unlock();
        }*/
    }

    public EMCInventory getEMCInventory(UUID owner)
    {
        lock.lock();
        try
        {
            if (!emcInventories.containsKey(owner))
            {
                EMCInventory inv = new EMCInventory(owner, this);
                MinecraftForge.EVENT_BUS.register(inv);
                emcInventories.put(owner, inv);
            }
            return emcInventories.get(owner);
        }
        finally
        {
            lock.unlock();
        }
    }

    private void bustCache()
    {
        /*
        lock.lock();
        try
        {
            //cacheBlacklist.clear();
            //emcValues.clear();
        }
        finally
        {
            lock.unlock();
        }
        */
    }

    private void markDirty(UUID owner)
    {
        if(!dirtyPlayers.containsKey(owner))
        {
            dirtyPlayers.put(owner, TICK_DELAY);
        }
    }

    @Nullable
    private EntityPlayerMP getEntityPlayerMP(UUID owner)
    {
        EntityPlayerMP player = null;
        MinecraftServer server = world.getMinecraftServer();
        if (server != null)
        {
            player = server.getPlayerList().getPlayerByUUID(owner);
        }
        return player;
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        tick();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        UUID owner = event.player.getUniqueID();

        playerLoggedIn(owner);
    }

    public void onEmcRemap(EMCRemapEvent event)
    {
        bustCache();
    }

    public void unload()
    {
        lock.lock();
        try
        {
            for (EMCInventory inv : emcInventories.values())
            {
                MinecraftForge.EVENT_BUS.unregister(inv);
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    private ItemStack getIdeal(ItemStack stack)
    {
        ItemStack idealStack = stack;
        if(idealStack.getCount() != 1 || idealStack.getItemDamage() != 0)
        {
            idealStack = idealStack.copy();
            idealStack.setItemDamage(0);
            idealStack.setCount(1);
        }
        return idealStack;
    }

}
