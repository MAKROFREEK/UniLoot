package net.smaa.uniloot.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class SerializationUtil {

    /**
     * Converts an array of ItemStacks into a Base64 encoded string.
     * @param items The array of items to serialize.
     * @return A Base64 string representing the item array.
     */
    public static String itemStackArrayToBase64(ItemStack[] items) {
        if (items == null) {
            return "";
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the array
            dataOutput.writeInt(items.length);

            // Write each itemStack
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    /**
     * Converts a Base64 encoded string back into an array of ItemStacks.
     * @param data The Base64 string to deserialize.
     * @return An array of ItemStacks.
     * @throws IOException If the data is malformed.
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Read the size of the array
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            // Read each itemStack
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
