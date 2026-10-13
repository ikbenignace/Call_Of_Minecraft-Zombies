package com.theprogrammingturkey.comz.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Spawns an entity with its setup applied before it is added to the world, so clients never see it
 * unconfigured for a tick. The callback parameter of {@code World#spawn} is
 * {@code java.util.function.Consumer} from 1.20.3 and {@code org.bukkit.util.Consumer} before, so the
 * matching overload is resolved reflectively.
 */
public final class EntitySpawner
{
	private static final Method SPAWN_JAVA = findSpawn(Consumer.class);
	private static final Class<?> BUKKIT_CONSUMER = findClass("org.bukkit.util.Consumer");
	private static final Method SPAWN_BUKKIT = BUKKIT_CONSUMER == null ? null : findSpawn(BUKKIT_CONSUMER);

	private EntitySpawner()
	{
	}

	public static <T extends Entity> T spawn(World world, Location loc, Class<T> type, Consumer<T> setup)
	{
		try
		{
			if(SPAWN_JAVA != null)
				return type.cast(SPAWN_JAVA.invoke(world, loc, type, setup));
			if(SPAWN_BUKKIT != null)
			{
				Object bukkitConsumer = Proxy.newProxyInstance(EntitySpawner.class.getClassLoader(), new Class<?>[]{BUKKIT_CONSUMER}, (proxy, method, args) ->
				{
					if(method.getName().equals("accept"))
					{
						setup.accept(type.cast(args[0]));
						return null;
					}
					return method.invoke(setup, args);
				});
				return type.cast(SPAWN_BUKKIT.invoke(world, loc, type, bukkitConsumer));
			}
		} catch(ReflectiveOperationException e)
		{
			throw new IllegalStateException("Could not spawn " + type.getSimpleName(), e);
		}
		T entity = world.spawn(loc, type);
		setup.accept(entity);
		return entity;
	}

	private static Method findSpawn(Class<?> consumerType)
	{
		try
		{
			return World.class.getMethod("spawn", Location.class, Class.class, consumerType);
		} catch(NoSuchMethodException e)
		{
			return null;
		}
	}

	private static Class<?> findClass(String name)
	{
		try
		{
			return Class.forName(name);
		} catch(ClassNotFoundException e)
		{
			return null;
		}
	}
}
