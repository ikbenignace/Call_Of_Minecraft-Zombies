package com.theprogrammingturkey.comz.spawning.special;

import java.util.Locale;

public enum SpecialBehaviour
{
	STEAL_PERK,
	STEAL_WEAPON,
	BOSS,
	NAPALM,
	SHRIEKER;

	public static SpecialBehaviour fromId(String id)
	{
		if(id == null)
			return null;
		try
		{
			return valueOf(id.trim().toUpperCase(Locale.ROOT));
		} catch(IllegalArgumentException e)
		{
			return null;
		}
	}

	public String id()
	{
		return name().toLowerCase(Locale.ROOT);
	}
}
