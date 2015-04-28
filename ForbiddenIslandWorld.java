import java.awt.Color;

import tester.*;
import javalib.impworld.*;
import javalib.colors.*;
import javalib.worldcanvas.*;
import javalib.worldimages.*;

import java.util.*;

interface IFunc<T, R> {
    R apply(T element, R base);
}
class RenderImage implements IFunc<Cell, WorldImage> {
    int waterHeight;
    RenderImage(int waterHeight) {
        this.waterHeight = waterHeight;
    }
    public WorldImage apply(Cell element, WorldImage base) {
        return new OverlayImages(element.renderCell(waterHeight), base);
    }
}
interface IList<T> extends Iterable<T>{
    T get(int index);
    <R> R foldr(R base, IFunc<T, R> func);
    Cons<T> asCons();
    public Iterator<T> iterator();
}
class IListIterator<T> implements Iterator<T> {

    IList<T> list;
    IListIterator(IList<T> list) {
        this.list = list;
    }
    public boolean hasNext() {
        return !(this.list instanceof Empty);
    }


    @Override
    public T next() {
        Cons<T> l = this.list.asCons();
        T first = l.first;
        list = l.rest;
        return first;

    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove");

    }

}
class Cons<T> implements IList<T> {
    T first;
    IList<T> rest;
    Cons(T first, IList<T> rest) {
        this.first = first;
        this.rest = rest;
    }
    public Cons<T> asCons() {
        return this;
    }

    public T get(int index) {
        if(index == 0) {
            return this.first;
        }
        else {
            return this.rest.get(index - 1);
        }
    }

    public Iterator<T> iterator() {
        return new IListIterator<T>(this);
    }
    public <R> R foldr(R base, IFunc<T, R> func) {
        base = func.apply(this.first, base);
        return this.rest.foldr(base, func);
    }
}
class Empty<T> implements IList<T> {

    public Cons<T> asCons() {
        throw new RuntimeException("Cannot make an empty into a cons");
    }

    public T get(int index) {
        throw new RuntimeException("index out of bounds");
    }
    public <R> R foldr(R base, IFunc<T, R> func) {
        return base;
    }
    public Iterator<T> iterator() {
        return new IListIterator<T>(this);
    }
}
// Represents a single square of the game area
class Cell {
    // represents absolute height of this cell, in feet
    double height;
    // In logical coordinates, with the origin at the top-left corner of the scren
    int x, y;
    // the four adjacent cells to this one
    Cell left, top, right, bottom;
    // reports whether this cell is flooded or not
    boolean isFlooded;
    Cell(double height, int x, int y, Cell left, Cell top, Cell right, Cell bottom, boolean isFlooded) {
        this.height = height;
        this.x = x;
        this.y = y;
        this.top = top;
        this.left = left;
        this.right = right;
        this.bottom = bottom;
        this.isFlooded = isFlooded;
    }
    void setLeft(Cell that) {
        this.left = that;
        that.right = this;
    }
    void setTop(Cell that) {
        this.top = that;
        that.bottom = this;
    }
    void adjustForFlooding(int waterHeight) {
        if(this.isFlooded) {
            return;
        }
        boolean onCoast = this.left.isFlooded || this.top.isFlooded ||
                this.right.isFlooded || this.bottom.isFlooded;
        if(onCoast) {
            if(this.height <= waterHeight) {
                this.isFlooded = true;
                this.left.adjustForFlooding(waterHeight);
                this.right.adjustForFlooding(waterHeight);
                this.bottom.adjustForFlooding(waterHeight);
                this.top.adjustForFlooding(waterHeight);
            }
        }
    }

    // render this cell
    RectangleImage renderCell(int waterHeight) {
        if (!this.isFlooded) {
            if(this.height > waterHeight) {
                return new RectangleImage(new Posn(this.x * 8 + 4, this.y * 8 + 4),
                        8,
                        8,
                        new Color((int)(Math.min(this.height - waterHeight, 32) * 8 - 1),
                                255,
                                (int)(Math.min(this.height - waterHeight, 32) * 8 - 1)));
            }
            return new RectangleImage(new Posn(this.x * 8 + 4, this.y * 8 + 4),
                    8,
                    8,
                    new Color(((int)(Math.min((Math.min(waterHeight - this.height, 11) * 16 + 64), 255))),
                            (Math.min(255 - (int)(Math.min(waterHeight - this.height, 31) * 8 + 1), 64)), 0));

        }
        else {
            return new RectangleImage(new Posn(this.x * 8 + 4, this.y * 8 + 4),
                    8,
                    8,
                    new Color(0,
                            0,
                            (int)(255 - (Math.min(this.height, 31) * 8)))); 
        }
    }
}
class OceanCell extends Cell {
    OceanCell(double height, int x, int y, Cell left, Cell top, Cell right, Cell bottom) {
        super(height, x, y, left, top, right, bottom, true);
    }

    RectangleImage renderCell() {
        return new RectangleImage(new Posn(this.x*8 + 4, this.y*8 + 4),
                8,
                8,
                new Blue());
    }
}
class ForbiddenIslandWorld extends World {
    // All the cells of the game, including the ocean
    IList<Cell> board;

    Player players;

    ArrayList<Piece> pickups;

    Helicopter copter;

    // -1 if lost, 1 if won, 0 if not over
    int gameOver;
    int score;
    
    boolean pause;

    // the current height of the ocean
    int waterHeight;
    int count;
    static final int ISLAND_SIZE = 64;
    ForbiddenIslandWorld(String mode) {
        this.reset(mode);
        this.pause = true;
    }
    public void reset(String mode) {
        this.score = 0;
        this.waterHeight = 0;
        this.count = 0;
        this.gameOver = 0;
        this.pause = false;
        if (mode.equals("m")) {
            this.board = this.makeOrderedBoard();
        }
        else if (mode.equals("r")) {
            this.board = this.makeRandomBoard();
        }
        else if (mode.equals("t")) {
            this.board = this.makeAnnoyingBoard();
        }
        this.players = new Player(this.board);
        this.pickups = new ArrayList<Piece>();
        for(int i = 0; i < 4; i++) {
            this.pickups.add(new Piece(this.board));
        }
        for(int i = 0; i < pickups.size(); i++) {
            Piece curr = pickups.get(i);
            for(int j = i; j > 0; j--) {
                if(curr.posn.equals(pickups.get(j - 1).posn)) {
                    curr.posn = curr.getRandomCell(this.board);
                    j = i;
                }
            }
        }
        this.copter = new Helicopter(this.board);
    }

    public void onKeyEvent(String ke) {
        if (ke.equals("m") ||
                ke.equals("r") ||
                ke.equals("t")) {
            this.reset(ke);
        }
        else if (ke.equals("p")) {
            this.pause = !this.pause;
        }
        else {
            players.onKeyEvent(ke);
        }
    }
    public void onTick() {
        if(this.gameOver == 0 && !this.pause) {
            this.checkForCollisions();
            this.adjustForFlooding();
            this.endIfReady();
        }
        
    }

    public void endIfReady(){
        if (this.players.posn.isFlooded) {
            this.gameOver = -1;
        }
        if ((this.players.posn == this.copter.posn)
                && this.players.inventory.size() >= 3) {
            this.gameOver = 1;
            this.score = (600 - (this.waterHeight * 10 + count)) * this.players.inventory.size();
        }
    }

    public void checkForCollisions() {
        for(int i = 0; i < pickups.size(); i++) {
            Piece curr = pickups.get(i);
            if(curr.posn.equals(this.players.posn)) {
                this.players.inventory.add(pickups.remove(i));
                i--;
            }
        }
    }
    public void adjustForFlooding() {
        count++;
        if(count != 10) {
            return;
        }
        else {
            this.waterHeight++;
            count = 0;
            for(Cell c: this.board) {
                c.adjustForFlooding(this.waterHeight);
            }
        }
    }

    public IList<Cell> makeOrderedBoard() {
        ArrayList<ArrayList<Double>> heights = new ArrayList<ArrayList<Double>>();
        for(int y = 0; y < ForbiddenIslandWorld.ISLAND_SIZE+1; y++) {
            ArrayList<Double> row = new ArrayList<Double>();
            for(int x = 0; x < ForbiddenIslandWorld.ISLAND_SIZE+1; x++) {
                Double height = this.makeOrderedHeight(x, y);
                row.add(height);
            }
            heights.add(row);
        }
        IList<Cell> ret = this.convertToCells(heights);
        return ret;
    }
    public Double makeOrderedHeight(int x, int y) {
        int center = ForbiddenIslandWorld.ISLAND_SIZE/2;
        double dist = Math.abs(center - x) + Math.abs(center - y);
        if(dist > center) {
            return 0.0;
        }
        else {
            return ForbiddenIslandWorld.ISLAND_SIZE/2 - dist;
        }
    }




    public IList<Cell> makeRandomBoard() {
        ArrayList<ArrayList<Double>> heights = new ArrayList<ArrayList<Double>>();
        for(int y = 0; y < ForbiddenIslandWorld.ISLAND_SIZE+1; y++) {
            ArrayList<Double> row = new ArrayList<Double>();
            for(int x = 0; x < ForbiddenIslandWorld.ISLAND_SIZE+1; x++) {
                Double height = this.makeRandomHeight(x, y);
                row.add(height);
            }
            heights.add(row);
        }
        IList<Cell> ret = this.convertToCells(heights);
        return ret;
    }
    public Double makeRandomHeight(int x, int y) {
        int center = ForbiddenIslandWorld.ISLAND_SIZE/2;
        double dist = Math.abs(center - x) + Math.abs(center - y);
        if(dist > center) {
            return 0.0;
        }
        else {
            return ((double)new Random().nextInt(31) + 1);
        }
    }





    public IList<Cell> makeAnnoyingBoard() {
        ArrayList<ArrayList<Double>> heights = this.makeAnnoyingHeight();
        heights.get(ForbiddenIslandWorld.ISLAND_SIZE/2).set(ForbiddenIslandWorld.ISLAND_SIZE/2, 32.0);
        heights.get(0).set(ForbiddenIslandWorld.ISLAND_SIZE/2, 1.0);
        heights.get(ForbiddenIslandWorld.ISLAND_SIZE).set(ForbiddenIslandWorld.ISLAND_SIZE/2, 1.0);
        heights.get(ForbiddenIslandWorld.ISLAND_SIZE/2).set(0, 1.0);
        heights.get(ForbiddenIslandWorld.ISLAND_SIZE/2).set(ForbiddenIslandWorld.ISLAND_SIZE, 1.0);
        this.makeQuadrant(heights, ForbiddenIslandWorld.ISLAND_SIZE/2, ForbiddenIslandWorld.ISLAND_SIZE/2, ForbiddenIslandWorld.ISLAND_SIZE, ForbiddenIslandWorld.ISLAND_SIZE, 4);
        this.makeQuadrant(heights, 0, ForbiddenIslandWorld.ISLAND_SIZE/2, ForbiddenIslandWorld.ISLAND_SIZE/2, ForbiddenIslandWorld.ISLAND_SIZE, 3);
        this.makeQuadrant(heights, ForbiddenIslandWorld.ISLAND_SIZE/2, 0, ForbiddenIslandWorld.ISLAND_SIZE, ForbiddenIslandWorld.ISLAND_SIZE/2, 2);
        this.makeQuadrant(heights, 0, 0, ForbiddenIslandWorld.ISLAND_SIZE/2, ForbiddenIslandWorld.ISLAND_SIZE/2, 1);

        return this.convertToCells(heights);
    }
    public void makeQuadrant(ArrayList<ArrayList<Double>> heights, int x1, int y1, int x2, int y2, int quadrant) {
        if(y1 + 1 >= y2 || x1 + 1 >= x2) {
            return;
        }
        double tl = heights.get(y1).get(x1);
        double tr = heights.get(y2).get(x1);
        double bl = heights.get(y1).get(x2);
        double br = heights.get(y2).get(x2);
        double t = (Math.random() - .5)*((x2 - x1) * (y2 - y1) * .1) + (tl + tr)/2;
        heights.get((y1+y2)/2).set(x1, t);
        double l = (Math.random() - .5)*((x2 - x1) * (y2 - y1) * .1) + (tl + bl)/2;
        heights.get(y1).set((x1+x2)/2, l);
        if(quadrant > 2) {
            double b = (Math.random() - .5)*((x2 - x1) * (y2 - y1) * .1) + (bl + br)/2;
            heights.get((y1+y2)/2).set(x2, b);
        }
        if(quadrant == 2 || quadrant == 4) {
            double r = (Math.random() - .5)*((x2 - x1) * (y2 - y1) * .1) + (tr + br)/2;
            heights.get(y2).set((x1+x2)/2, r); 
        }

        double m = (Math.random() - .5)*((x2 - x1) * (y2 - y1) * .1) + (tl + tr + bl + br)/4;
        heights.get((y1+y2)/2).set((x1+x2)/2, m);
        this.makeQuadrant(heights, (x1+x2)/2, (y1+y2)/2, x2, y2, 4);
        this.makeQuadrant(heights, x1, (y1+y2)/2, (x1+x2)/2, y2, 3);
        this.makeQuadrant(heights, (x1+x2)/2, y1, x2, (y1+y2)/2, 2);

        this.makeQuadrant(heights, x1, y1, (x1+x2)/2, (y1+y2)/2, 1);
    }

    public ArrayList<ArrayList<Double>> makeAnnoyingHeight() {
        ArrayList<ArrayList<Double>> heights = new ArrayList<ArrayList<Double>>();
        for (int x = 0; x < ForbiddenIslandWorld.ISLAND_SIZE+1; x++) {
            ArrayList<Double> row = new ArrayList<Double>();
            for (int y = 0; y < ForbiddenIslandWorld.ISLAND_SIZE+1; y++) {
                row.add(0.0);
            }
            heights.add(row);
        }
        return heights;
    }
    public IList<Cell> convertToCells(ArrayList<ArrayList<Double>> heights) {
        ArrayList<ArrayList<Cell>> inter = new ArrayList<ArrayList<Cell>>();
        for(int i = 0; i < heights.size(); i++) {
            ArrayList<Double> row = heights.get(i);
            ArrayList<Cell> curr = new ArrayList<Cell>();

            for(int j = 0; j < row.size(); j++) {
                Cell temp;
                if(row.get(j) <= 0) {
                    temp = new OceanCell(0, j, i, null, null, null, null);
                    temp.right = temp;
                    temp.bottom = temp;
                }
                else {
                    temp = new Cell(row.get(j), j, i, null, null, null, null, false);
                    temp.right = temp;
                    temp.bottom = temp;
                }

                if(i == 0) {
                    temp.setTop(temp);
                }
                else {
                    temp.setTop(inter.get(i - 1).get(j));
                }
                if(j == 0) {
                    temp.setLeft(temp);
                }
                else {

                    temp.setLeft(curr.get(j - 1));
                }
                curr.add(temp);
            }
            inter.add(curr);
        }
        return this.convertHelper(inter);
    }
    public IList<Cell> convertHelper(ArrayList<ArrayList<Cell>> board) {
        IList<Cell> ret = new Empty<Cell>();
        for(ArrayList<Cell> r: board) {
            for(Cell c: r) {
                ret = new Cons<Cell>(c, ret);
            }
        }
        return ret;
    }
    public WorldImage makeImage() {
        if(this.pause) {
            WorldImage image = new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 - 30), "PAUSE", 50, 0, new Green());
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4), "Press m, t, or r to play, press p to pause.", 25, 0, new Green()));
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 + 30), "Collect three pieces and return to the.", 25, 0, new Green()));
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 + 60), "helicopter to win. Watch out for floods!", 25, 0, new Green()));
            return image;
            
        }
        if (this.gameOver == 0) {
            WorldImage image = this.players.renderPlayer();

            for(Cell c: this.board) {
                image = new OverlayImages(image, c.renderCell(this.waterHeight));
            }
            for(Piece p: this.pickups) {
                image = new OverlayImages(image, p.renderPiece());
            }
            image = image.overlayImages(copter.renderPiece());
            image = image.overlayImages(this.players.renderPlayer());
            return image;
        }
        else if (this.gameOver == 1) {
            WorldImage image = new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 - 30), "YOU WIN", 50, 0, new Blue());
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4), "Score: " + this.score, 25, 0, new Blue()));
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 + 30), "Press m, t, or r to play again", 25, 0, new Blue()));
            return image;
        }
        else {
            WorldImage image = new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4), "YOU LOSE", 50, 0, new Red());
            image = image.overlayImages(new TextImage(new Posn(ForbiddenIslandWorld.ISLAND_SIZE * 4,
                    ForbiddenIslandWorld.ISLAND_SIZE * 4 + 50), "Press m, t, or r to play again", 25, 0, new Red()));
            return image;
        }
        
    }
}
class Piece {
    Cell posn;

    Piece(IList<Cell> board) {
        this.posn = this.getRandomCell(board);
    }

    Cell getRandomCell(IList<Cell> l) {
        Cell curr = l.get(new Random().nextInt((int)Math.pow(ForbiddenIslandWorld.ISLAND_SIZE + 1, 2) - 1));
        if (curr.isFlooded) {
            return this.getRandomCell(l);
        }
        else {
            return curr;
        }
    }
    WorldImage renderPiece() {
        return new DiskImage(new Posn(posn.x*8 + 4, posn.y*8 + 4), 4, Color.MAGENTA);
    }
}
class Helicopter extends Piece {
    Helicopter(IList<Cell> board) {
        super(board);
    }
    Cell getRandomCell(IList<Cell> l) {
        Cell curr = l.get(0);
        for(Cell c: l) {
            if(c.height > curr.height) {
                curr = c;
            }
        }
        return curr;
    }
    WorldImage renderPiece() {
        return new FromFileImage(new Posn(posn.x * 8 + 4, posn.y * 8 + 4), "helicopter.png");
    }
}
class Player {
    Cell posn;
    ArrayList<Piece> inventory;
    Player(IList<Cell> board) {
        this.posn = this.getRandomCell(board);
        this.inventory = new ArrayList<Piece>();
    }

    Cell getRandomCell(IList<Cell> l) {
        Cell curr = l.get(new Random().nextInt((int)Math.pow(ForbiddenIslandWorld.ISLAND_SIZE + 1, 2) - 1));
        if (curr.isFlooded) {
            return this.getRandomCell(l);
        }
        else {
            return curr;
        }
    }
    public WorldImage renderPlayer() {
        return new DiskImage(new Posn(posn.x*8 + 4, posn.y*8 + 4), 8, new Yellow());
    }
    void onKeyEvent(String ke) {
        if(ke.equals("up")) {
            if(!this.posn.top.isFlooded) {
                this.posn = this.posn.top;
            }
        }
        if(ke.equals("down")) {
            if(!this.posn.bottom.isFlooded) {
                this.posn = this.posn.bottom;
            }
        }
        if(ke.equals("left")) {
            if(!this.posn.left.isFlooded) {
                this.posn = this.posn.left;
            }
        }
        if(ke.equals("right")) {
            if(!this.posn.right.isFlooded) {
                this.posn = this.posn.right;
            }
        }
    }
}


class ExamplesForbiddenIsland {
    ForbiddenIslandWorld w1;
    
    
    void initRegMount() {
        this.w1 = new ForbiddenIslandWorld("m");
    }
    void initAnnoyingMount(Tester t) {
        this.w1 = new ForbiddenIslandWorld("t");
    }

    void initRandMount(Tester t) {
        this.w1 = new ForbiddenIslandWorld("r");
    }
    /*void startGame(Tester t) {
        t.checkExpect(1, 1);
        this.w1 = new ForbiddenIslandWorld("m");
        this.w1.bigBang(8*ForbiddenIslandWorld.ISLAND_SIZE + 8, 8*ForbiddenIslandWorld.ISLAND_SIZE + 8, .15);
        
    }*/
    void testOnKeyEvent(Tester t) {
        this.initRegMount();
        Player p = this.w1.players;
        Cell posn = p.posn;
        Cell right = posn.right;
        Cell top = posn.top;
        w1.onKeyEvent("up");
        Cell curTop = w1.players.posn;
        //t.checkExpect(top, curTop);
        
    }
    void testOnKeyTwo(Tester t) {
        this.initRegMount();
        Player p = this.w1.players;
        Cell posn = p.posn;
        Cell right = posn.right;
        if(right.isFlooded) {
            right = right.left;
        }
        Cell top = posn.top;
        if(top.isFlooded) {
            top = top.bottom;
            posn = posn.bottom;
            right = right.bottom;
        }
        w1.onKeyEvent("up");
        Cell curr = w1.players.posn;
        t.checkExpect(curr.x, top.x);
        t.checkExpect(curr.y, top.y);
        w1.onKeyEvent("down");
        curr = w1.players.posn;
        t.checkExpect(posn.x, curr.x);
        t.checkExpect(posn.y, curr.y);
        w1.onKeyEvent("right");
        curr = w1.players.posn;
        t.checkExpect(right.x, curr.x);
        t.checkExpect(right.y, curr.y);
        w1.onKeyEvent("p");
        t.checkExpect(w1.pause, false);
        w1.onKeyEvent("m");
        t.checkExpect(!w1.players.posn.equals(right));
    }
    void testSet(Tester t) {
        Cell mid = new Cell(1, 1, 1, null, null, null, null, false);
        Cell left = new Cell(1, 0, 0, null, null, null, null, false);
        Cell top = new Cell (1, 2, 2, null, null, null, null, false);
        mid.setTop(top);
        mid.setLeft(left);
        t.checkExpect(mid.top, top);
        t.checkExpect(top.bottom, mid);
        t.checkExpect(mid.left, left);
        t.checkExpect(left.right, mid);
    }
    void testConvertHelper(Tester t) {
        this.initRegMount();
        ArrayList<ArrayList<Cell>> board = new ArrayList<ArrayList<Cell>>();
        Cell c1 = new Cell(1, 1, 1, null, null, null, null, false);
        Cell c2 = new Cell(1, 2, 3, null, null, null, null, false);
        ArrayList<Cell> r1 = new ArrayList<Cell>();
        r1.add(c2);
        r1.add(c1);
        board.add(r1);
        Cell c3 = new Cell(1, 1, 1, null, null, null, null, false);
        Cell c4 = new Cell(1, 2, 3, null, null, null, null, false);
        ArrayList<Cell> r2 = new ArrayList<Cell>();
        r2.add(c4);
        r2.add(c3);
        board.add(r2);
        IList<Cell> conv = new Cons<Cell>(c1, new Cons<Cell>(c2,
                new Cons<Cell>(c3, new Cons<Cell>(c4, new Empty<Cell>()))));
        t.checkExpect(w1.convertHelper(board), conv);
        
    }
    void testRenderCell(Tester t) {
        Cell c = new Cell(1, 1, 1, null, null, null, null, false);
        WorldImage img = c.renderCell(0);
        t.checkExpect(img.color.getBlue(), 7);
        WorldImage img2 = c.renderCell(2);
        t.checkExpect(img2.color.getRed(), 80);
    }
    void testGame(Tester t) {
        this.initRegMount();
        w1.bigBang(8*ForbiddenIslandWorld.ISLAND_SIZE + 8, 8*ForbiddenIslandWorld.ISLAND_SIZE + 8, .15);
        t.checkExpect(1, 1);
    }
    
    



}







