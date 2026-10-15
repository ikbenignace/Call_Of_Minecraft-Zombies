package com.theprogrammingturkey.comz.game.builder;

import com.theprogrammingturkey.comz.game.features.Zone;

/**
 * Starting flags for a zone placed with the Feature tool. Each preset is one Feature-tool variant, so F
 * cycles through them; every flag can be changed afterwards via the zone's chat prompt.
 */
public enum ZonePreset
{
	LOW_GRAVITY("Zone: Low gravity"),
	NO_AIR("Zone: No air"),
	DAMAGE("Zone: Damage"),
	NAMED_AREA("Zone: Named area");

	private static final double LOW_GRAVITY_VALUE = 0.4;
	private static final double DAMAGE_PER_SECOND = 2.0;

	private final String label;

	ZonePreset(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public static ZonePreset fromLabel(String label)
	{
		for(ZonePreset preset : values())
			if(preset.label.equals(label))
				return preset;
		return null;
	}

	public void apply(Zone zone)
	{
		switch(this)
		{
			case LOW_GRAVITY:
				zone.setGravity(LOW_GRAVITY_VALUE);
				break;
			case NO_AIR:
				zone.setNoAir(true);
				break;
			case DAMAGE:
				zone.setDamagePerSecond(DAMAGE_PER_SECOND);
				break;
			case NAMED_AREA:
				break;
		}
	}
}
