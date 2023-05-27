package fr.ul.miage.reseau.serveur;

public class ServeurMaster {

    public static void main(String[] args) {
        Serveur serveurPrincipal = new Serveur(false);
        serveurPrincipal.run();
    }
}
