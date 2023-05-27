package fr.ul.miage.reseau.serveur;

public class ServeurSlave {

    public static void main(String[] args) {
        Serveur serveurSlave = new Serveur(true);
        serveurSlave.run();
    }
}
