package com.theprogrammingturkey.comz.game.equipment;

import java.util.Locale;

public enum EquipmentType
{
	MELEE,
	PLACEABLE,
	TACTICAL,
	GEAR;

	public static EquipmentType fromId(String id)
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
}
