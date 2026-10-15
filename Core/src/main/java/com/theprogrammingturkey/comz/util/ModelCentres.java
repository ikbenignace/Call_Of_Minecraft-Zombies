package com.theprogrammingturkey.comz.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.util.Vector;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Where each pack item model's geometry sits relative to an item display's origin
 * ({@code model_centres.json}, generated from the pack by {@code tools/model_centres.py}). Gun models are
 * authored around the grip, so without this a gun drawn at a spot hangs off to one side of it.
 */
public final class ModelCentres
{
	private static Map<String, double[]> centres;

	private ModelCentres()
	{
	}

	/**
	 * World-space shift to add to a display's location so the model's visual centre lands on that location,
	 * for a display drawn at {@code scale} and turned {@code yawDegrees} about Y. Null when the model is
	 * unknown or the pack is off.
	 */
	public static Vector shift(String itemModel, float scale, float yawDegrees)
	{
		if(itemModel == null || !PackModels.isPackEnabled())
			return null;
		double[] c = table().get(itemModel.indexOf(':') < 0 ? "comz:" + itemModel : itemModel);
		if(c == null)
			return null;
		double yaw = Math.toRadians(yawDegrees);
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double x = c[0] * cos + c[2] * sin;
		double z = -c[0] * sin + c[2] * cos;
		return new Vector(-x * scale, -c[1] * scale, -z * scale);
	}

	/** Longest side of the model in blocks at scale 1, or 0 when unknown. */
	public static double length(String itemModel)
	{
		if(itemModel == null || !PackModels.isPackEnabled())
			return 0;
		double[] c = table().get(itemModel.indexOf(':') < 0 ? "comz:" + itemModel : itemModel);
		return c == null || c.length < 4 ? 0 : c[3];
	}

	private static synchronized Map<String, double[]> table()
	{
		if(centres != null)
			return centres;
		centres = new HashMap<>();
		try(InputStream in = COMZombies.getPlugin().getResource("model_centres.json"))
		{
			if(in == null)
				return centres;
			try(Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				@SuppressWarnings("deprecation")
				JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
				for(Map.Entry<String, JsonElement> entry : root.entrySet())
				{
					JsonArray v = entry.getValue().getAsJsonArray();
					double[] values = new double[v.size()];
					for(int i = 0; i < values.length; i++)
						values[i] = v.get(i).getAsDouble();
					centres.put(entry.getKey(), values);
				}
			}
		} catch(Exception e)
		{
			COMZombies.log.warning("Could not read model_centres.json: " + e.getMessage());
		}
		return centres;
	}
}
