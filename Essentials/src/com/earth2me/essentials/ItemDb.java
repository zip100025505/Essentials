package com.earth2me.essentials;

import net.ess3.api.IEssentials;
import com.earth2me.essentials.utils.StringUtil;
import static com.earth2me.essentials.I18n._;
import com.earth2me.essentials.utils.NumberUtil;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;


public class ItemDb implements IConf, net.ess3.api.IItemDb
{
	private final transient IEssentials ess;

	public ItemDb(final IEssentials ess)
	{
		this.ess = ess;
		file = new ManagedFile("items.csv", ess);
	}
	private final transient Map<String, Material> items = new HashMap<String, Material>();
	private final transient Map<ItemData, List<String>> names = new HashMap<ItemData, List<String>>();
	private final transient Map<ItemData, String> primaryName = new HashMap<ItemData, String>();
	private final transient Map<String, Short> durabilities = new HashMap<String, Short>();
	private final transient ManagedFile file;
	private final transient Pattern splitPattern = Pattern.compile("[:+',;.]");

	@Override
	public void reloadConfig()
	{
		final List<String> lines = file.getLines();

		if (lines.isEmpty())
		{
			return;
		}

		durabilities.clear();
		items.clear();
		names.clear();
		primaryName.clear();

		for (String line : lines)
		{
			line = line.trim().toLowerCase(Locale.ENGLISH);
			if (line.length() > 0 && line.charAt(0) == '#')
			{
				continue;
			}

			final String[] parts = line.split("[^a-z0-9]");
			if (parts.length < 2)
			{
				continue;
			}

			final Material numeric = Material.getMaterial(parts[1]);
			final short data = parts.length > 2 && !parts[2].equals("0") ? Short.parseShort(parts[2]) : 0;
			String itemName = parts[0].toLowerCase(Locale.ENGLISH);

			durabilities.put(itemName, data);
			items.put(itemName, numeric);

			ItemData itemData = new ItemData(numeric, data);
			if (names.containsKey(itemData))
			{
				List<String> nameList = names.get(itemData);
				nameList.add(itemName);
				Collections.sort(nameList, new LengthCompare());
			}
			else
			{				
				List<String> nameList = new ArrayList<String>();
				nameList.add(itemName);
				names.put(itemData, nameList);
				primaryName.put(itemData, itemName);
			}
		}
	}

	@Override
	public ItemStack get(final String id, final int quantity) throws Exception
	{
		final ItemStack retval = get(id.toLowerCase(Locale.ENGLISH));
		retval.setAmount(quantity);
		return retval;
	}

	@Override
	public ItemStack get(final String id) throws Exception
	{
		Material itemid = null;
		String itemname = null;
		short metaData = 0;
		String[] parts = splitPattern.split(id);
		if (id.matches("^\\d+[:+',;.]\\d+$"))
		{
			itemid = Material.getMaterial(parts[0]);
			metaData = Short.parseShort(parts[1]);
		}
		else if (NumberUtil.isInt(id))
		{
			itemid = Material.getMaterial(id);
		}
		else if (id.matches("^[^:+',;.]+[:+',;.]\\d+$"))
		{
			itemname = parts[0].toLowerCase(Locale.ENGLISH);
			metaData = Short.parseShort(parts[1]);
		}
		else
		{
			itemname = id.toLowerCase(Locale.ENGLISH);
		}

		if (itemname != null)
		{
			if (items.containsKey(itemname))
			{
				itemid = items.get(itemname);
				if (durabilities.containsKey(itemname) && metaData == 0)
				{
					metaData = durabilities.get(itemname);
				}
			}
			else if (Material.getMaterial(itemname.toUpperCase(Locale.ENGLISH)) != null)
			{
				itemid = Material.getMaterial(itemname.toUpperCase(Locale.ENGLISH));
				metaData = 0;
			}
			else
			{
				throw new Exception(_("unknownItemName", id));
			}
		}

		if (itemid == null)
		{
			throw new Exception(_("unknownItemId", itemid));
		}
		final ItemStack retval = new ItemStack(itemid);
		retval.setAmount(itemid.getMaxStackSize());
		retval.setDurability(metaData);
		return retval;
	}

	@Override
	public List<ItemStack> getMatching(User user, String[] args) throws Exception
	{
		List<ItemStack> is = new ArrayList<ItemStack>();

		if (args.length < 1)
		{
			is.add(user.getItemInHand());
		}
		else if (args[0].equalsIgnoreCase("hand"))
		{
			is.add(user.getItemInHand());
		}
		else if (args[0].equalsIgnoreCase("inventory") || args[0].equalsIgnoreCase("invent") || args[0].equalsIgnoreCase("all"))
		{
			for (ItemStack stack : user.getInventory().getContents())
			{
				if (stack == null || stack.getType() == Material.AIR)
				{
					continue;
				}
				is.add(stack);
			}
		}
		else if (args[0].equalsIgnoreCase("blocks"))
		{
			for (ItemStack stack : user.getInventory().getContents())
			{
				if (stack == null || !stack.getType().isBlock() || stack.getType() == Material.AIR)
				{
					continue;
				}
				is.add(stack);
			}
		}
		else
		{
			is.add(get(args[0]));
		}
		
		if (is.isEmpty() || is.get(0).getType() == Material.AIR)
		{
			throw new Exception(_("itemSellAir"));
		}
		
		return is;
	}

	@Override
	public String names(ItemStack item)
	{
		ItemData itemData = new ItemData(item.getType(), item.getDurability());
		List<String> nameList = names.get(itemData);
		if (nameList == null)
		{
			itemData = new ItemData(item.getType(), (short)0);
			nameList = names.get(itemData);
			if (nameList == null)
			{
				return null;
			}
		}

		if (nameList.size() > 15)
		{
			nameList = nameList.subList(0, 14);
		}
		return StringUtil.joinList(", ", nameList);
	}
	
@Override
	public String name(ItemStack item)
	{
		ItemData itemData = new ItemData(item.getType(), item.getDurability());
		String name = primaryName.get(itemData);
		if (name == null)
		{
			itemData = new ItemData(item.getType(), (short)0);
			name = primaryName.get(itemData);
			if (name == null)
			{
				return null;
			}
		}
		return name;
	}
	
	static class ItemData
	{
		final private Material itemType;
		final private short itemData;

		ItemData(final Material itemType, final short itemData)
		{
			this.itemType = itemType;
			this.itemData = itemData;
		}

		public Material getItem()
		{
			return itemType;
		}

		public short getItemData()
		{
			return itemData;
		}

		@Override
		public int hashCode()
		{
			return (31 * itemType.hashCode()) ^ itemData;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null)
			{
				return false;
			}
			if (!(o instanceof ItemData))
			{
				return false;
			}
			ItemData pairo = (ItemData)o;
			return this.itemType == pairo.getItem()
				   && this.itemData == pairo.getItemData();
		}
	}


	class LengthCompare implements java.util.Comparator<String>
	{
		public LengthCompare()
		{
			super();
		}

		@Override
		public int compare(String s1, String s2)
		{
			return s1.length() - s2.length();
		}
	}
}
