package fr.ul.miage.reseau.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Envoi et lit les messages du serveur.
 * Ne gère que des chaînes d'au plus 80 **octets**.
 */
public class Client implements Runnable {

    public InputStream in;
    public OutputStream out;

    public static void main(String[] args)
    {
        (new Client()).run();
    }

    @Override
    public void run() {
        try {
            final InetAddress remoteAddress = InetAddress.getByName("127.0.0.1");
            try(final Socket server = new Socket(remoteAddress, 6379)) {
                this.out = server.getOutputStream();
                this.in = server.getInputStream();

                final BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

                final byte[] buffer = new byte[80];
                while (true)
                {
                    // Soit on attend l'envoi de la commande
                    if (System.in.available() > 0)
                    {
                        String line = br.readLine();
                        if (line != null)
                        {
                            // envoi de la chaîne au serveur
                            out.write(line.getBytes(StandardCharsets.ISO_8859_1));
                        }
                    }

                    // Soit on attend une réponse du serveur
                    if (in.available() > 0)
                    {
                        final int numRead = in.read(buffer);
                        System.out.println(new String(buffer, 0, numRead, StandardCharsets.ISO_8859_1));
                    }
                }
            }

        } catch(IOException e)
        {
            e.printStackTrace(System.err);
        }
    }

}