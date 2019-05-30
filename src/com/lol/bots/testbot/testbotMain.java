package com.lol.bots.testbot;

import com.runemate.game.api.hybrid.entities.Player;
import com.runemate.game.api.hybrid.entities.GameObject;
import com.runemate.game.api.hybrid.local.hud.interfaces.Bank;
import com.runemate.game.api.hybrid.local.hud.interfaces.Inventory;
import com.runemate.game.api.hybrid.location.Coordinate;
import com.runemate.game.api.hybrid.location.navigation.Traversal;
import com.runemate.game.api.hybrid.location.navigation.cognizant.RegionPath;
import com.runemate.game.api.hybrid.location.navigation.web.WebPath;
import com.runemate.game.api.hybrid.queries.results.LocatableEntityQueryResults;
import com.runemate.game.api.hybrid.queries.results.SpriteItemQueryResults;
import com.runemate.game.api.hybrid.region.GameObjects;
import com.runemate.game.api.hybrid.region.Players;
import com.runemate.game.api.script.Execution;
import com.runemate.game.api.script.framework.LoopingBot;

public class testbotMain extends LoopingBot {

    private static final int STATE_GOING_TO_SKILLING = 0;
    private static final int STATE_SKILLING = 1;
    private static final int STATE_GOING_TO_BANK = 2;
    private static final int STATE_BANKING = 3;


    private static final int BANK_SUB_STATE_OPEN = 0;
    private static final int BANK_SUB_STATE_DEPOSIT = 1;
    private static final int BANK_SUB_STATE_CLOSE = 2;

    private static final int SKILLING_SUB_STATE_STOPED = 0;
    private static final int SKILLING_SUB_STATE_WORKING = 1;
    private static final int SKILLING_SUB_STATE_DROPPING = 2;

    private static final Coordinate BANK_COORDINATES = new Coordinate(3183, 3443, 0);
    private static final Coordinate SKILLING_COORDINATES = new Coordinate(3137, 3431, 0);

    private static final String TREE_TO_CUT = "Willow";
    private static final String ITEM_TO_DROP = "Willow logs";

    private int currentState;
    private int currentBankState;
    private int currentSkillingState;

    private GameObject currentSpot;
    private int inventoryThreshold;

    private boolean moveTowardsCoordinate(Coordinate c){

        Player p = Players.getLocal();
        if(p != null && p.isValid() && p.getPosition().equals(c)){
            getLogger().debug("Arrived at destination.");
            return true;
        }

        RegionPath rp = RegionPath.buildTo(c);
        if(rp != null){
            rp.step();
            return false;
        }

        getLogger().debug("Region Path failed. Fallback to WebPath.");
        WebPath wp = Traversal.getDefaultWeb().getPathBuilder().buildTo(c);
        if(wp != null){
            wp.step();
            return false;
        }


        getLogger().debug("WebPath failed. Walking to random spot.");

        int currentX = p.getPosition().getX();
        int currentY = p.getPosition().getY();

        int targetX = c.getPosition().getX();
        int targetY = c.getPosition().getY();

        int deltaX = 0;
        int deltaY = 0;

        if(currentX > targetX) { deltaX = -5; }
        else{ deltaX = 5; }

        if(currentY > targetY) { deltaY = -5; }
        else{ deltaY = 5; }

        RegionPath fbrp = RegionPath.buildTo(new Coordinate(currentX + deltaX, currentY + deltaY, c.getPlane()));
        if(fbrp != null){
            fbrp.step();
        }
        return false;
    }


    @Override
    public void onLoop()
    {
        dropingLoop();
    }

    @Override
    public void onStart(String... arguments) {
        System.out.println("Running...");

        initDropingLoop();


    }

    private void initBankingLoop(){

        setLoopDelay(600, 900);

        currentState = STATE_GOING_TO_SKILLING;
        currentBankState = BANK_SUB_STATE_OPEN;
        currentSkillingState = SKILLING_SUB_STATE_STOPED;

    }
    private void bankingLoop(){

        switch (currentState){

            case STATE_GOING_TO_SKILLING:
                getLogger().debug("Going to Skill Spot");
                if(moveTowardsCoordinate(SKILLING_COORDINATES)){
                    LocatableEntityQueryResults<GameObject> spots = GameObjects.newQuery().names(TREE_TO_CUT).visible().results();
                    if(spots.size() > 0){
                        currentState = STATE_SKILLING;
                    }
                }
                break;
            case STATE_GOING_TO_BANK:
                getLogger().debug("Going to Bank Spot");
                if(moveTowardsCoordinate(BANK_COORDINATES)) {
                    LocatableEntityQueryResults<GameObject> banks = GameObjects.newQuery().names("Bank booth").visible().results();
                    if(banks.size() > 0){
                        currentState = STATE_BANKING;
                    }
                }
                break;
            case STATE_BANKING:
                getLogger().debug("Banking");
                GameObject bank = GameObjects.newQuery().names("Bank booth").visibility(5.0, 100.0).results().first();

                switch (currentBankState){
                    case BANK_SUB_STATE_OPEN:
                        if(Bank.open(bank)){
                            currentBankState = BANK_SUB_STATE_DEPOSIT;
                        }
                        break;
                    case BANK_SUB_STATE_DEPOSIT:
                        if(Bank.depositInventory()) {
                            currentBankState = BANK_SUB_STATE_CLOSE;
                        }
                        break;
                    case BANK_SUB_STATE_CLOSE:
                        if(Bank.close()) {
                            currentBankState = BANK_SUB_STATE_OPEN;
                            currentState = STATE_GOING_TO_SKILLING;
                        }
                        break;
                }
                break;
            case STATE_SKILLING:
                getLogger().debug("Skilling");

                switch (currentSkillingState){
                    case SKILLING_SUB_STATE_STOPED:
                        if(hasFoundSpot()){
                            currentSkillingState = SKILLING_SUB_STATE_WORKING;
                        }
                        break;
                    case SKILLING_SUB_STATE_WORKING:
                        if(!isSpotActive()) {
                            currentSkillingState = SKILLING_SUB_STATE_STOPED;
                        }
                        break;
                }

                if(Inventory.getEmptySlots() == 0){
                    currentSkillingState = SKILLING_SUB_STATE_STOPED;
                    currentState = STATE_GOING_TO_BANK;
                }

                break;


        }

    }


    private void initDropingLoop(){
        setLoopDelay(600, 900);
        currentSkillingState = SKILLING_SUB_STATE_STOPED;
        inventoryThreshold = -1;
    }
    private void dropingLoop() {


        switch (currentSkillingState){
            case SKILLING_SUB_STATE_STOPED:
                getLogger().info("STATE : STOPED");
                if(hasFoundSpot()){
                    currentSkillingState = SKILLING_SUB_STATE_WORKING;
                }
                break;
            case SKILLING_SUB_STATE_WORKING:
                getLogger().info("STATE : WORKING");
                if(!isSpotActive()) {
                    currentSkillingState = SKILLING_SUB_STATE_STOPED;
                }
                break;
            case SKILLING_SUB_STATE_DROPPING:
                getLogger().info("STATE : DROPPING");
                while(true){
                    SpriteItemQueryResults items = Inventory.newQuery().names(ITEM_TO_DROP).results();
                    if(items.size() == 0) {break;}
                    items.first().interact("Drop");
                }

                inventoryThreshold = -1;
                currentSkillingState = SKILLING_SUB_STATE_STOPED;
                break;
        }

        if(inventoryThreshold == -1){
            inventoryThreshold = (int)Math.round(Math.random() * 28);
            getLogger().debug("Threshold was -1. Now is " + inventoryThreshold);
        }

        getLogger().debug("Checking inventory");
        if(Inventory.getEmptySlots() <= inventoryThreshold) {
            currentSkillingState = SKILLING_SUB_STATE_DROPPING;
        }


    }

    private boolean hasFoundSpot(){
        currentSpot = GameObjects.newQuery().names(TREE_TO_CUT).visibility(50.0, 100.0).results().nearest();
        return currentSpot != null && currentSpot.interact("Chop down");
    }
    private boolean isSpotActive(){
        return currentSpot.isVisible();
    }

}
