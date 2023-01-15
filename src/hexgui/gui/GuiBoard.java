//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import hexgui.hex.*;
import hexgui.util.*;
import hexgui.game.Node;

import java.util.Vector;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigInteger;
import javax.swing.*;          
import javax.swing.border.EtchedBorder;
import java.awt.print.Printable;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;

//----------------------------------------------------------------------------

/** Gui Board. */
public final class GuiBoard
    extends JPanel implements Printable
{
    /** Callback for clicks on a field. */
    public interface Listener
    {
        void panelClicked();
	void fieldClicked(HexPoint point, boolean ctrl, boolean shift);
        void fieldDoubleClicked(HexPoint point, boolean ctrl, boolean shift);
    }

    private static final boolean DEFAULT_FLIPPED = true;
    
    public static final int HEXBOARD = 0;
    public static final int YBOARD = 1;

    /** Constructor. */
    public GuiBoard(Listener listener, GuiPreferences preferences)
    {
	m_image = null;
	m_listener = listener;
	m_preferences = preferences;
        m_arrows = new Vector<Pair<HexPoint, HexPoint>>();

	initSize(HEXBOARD, 
                 m_preferences.getInt("gui-board-width"),
		 m_preferences.getInt("gui-board-height"));

	setDrawType(m_preferences.get("gui-board-type"));   

	setPreferredSize(new Dimension
			 (m_preferences.getInt("gui-board-pixel-width"),
	  	          m_preferences.getInt("gui-board-pixel-height")));

        setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
	setLayout(new BoardLayout());
	m_boardPanel = new BoardPanel();
	add(m_boardPanel);

	MouseAdapter mouseAdapter = new MouseAdapter()
	{
	    public void mouseClicked(MouseEvent e)
	    {
                // First inform the parent that we were clicked, to
                // handle things like keyboard focus.
                m_listener.panelClicked();
		GuiField f = m_drawer.getFieldContaining(e.getPoint(), m_field);
		if (f == null) return;

                int modifiers = e.getModifiersEx();
                boolean ctrl = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0;
                boolean shift = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
                if (e.getClickCount() >= 2)
                    m_listener.fieldDoubleClicked(f.getPoint(), ctrl, shift);
                else
                    m_listener.fieldClicked(f.getPoint(), ctrl, shift);
	    }
	};
	m_boardPanel.addMouseListener(mouseAdapter);
        setCursorType("default");
	setVisible(true);
    }

    // Set the cursor to one of: "default", "black", "white",
    // "black-setup", "white-setup".
    public void setCursorType(String name)
    {
        String path;
        
        if (name.equals("white")) {
            path = "hexgui/images/cursor-white.png";
        } else if (name.equals("black")) {
            path = "hexgui/images/cursor-black.png";
        } else if (name.equals("white-setup")) {
            path = "hexgui/images/cursor-white-setup.png";
        } else if (name.equals("black-setup")) {
            path = "hexgui/images/cursor-black-setup.png";
        } else {
            path = null;
        }

        if (path == null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
            
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource(path);
        if (url == null) {
            System.out.println("setCursorType: could not load '" +
                               "hexgui/images/cursor-white.png" + "'!");
            return;
        }
        Image img = new ImageIcon(url).getImage();
        Point hot = new Point(8, 8);
        Toolkit t = getToolkit();
        Cursor c = t.createCustomCursor(img, hot, name);
        setCursor(c);
    }
    
    /** Sets the type of board drawer to use.  If <code>name</code> is
	not one of the known values, "Hex" is used.
	@param name one of ("Hex", "Go", "Y"). 
    */
    public void setDrawType(String name)
    {
        if (name.equals("Hex")) {
            if (m_mode != HEXBOARD)
                initSize(HEXBOARD, m_width, m_height);
	    m_drawer = new BoardDrawerHex();
	    m_preferences.put("gui-board-type", "Hex");
	} else {
	    System.out.println("GuiBoard: unknown draw type '" + name + "'.");
	    m_drawer = new BoardDrawerHex();
	} 
        repaint();
    }

    /** Sets whether black and letters is on top or if white and
	numbers is on top.  If string is invalid defaults to positive.
	@param orient either "Positive" or "Negative". 
    */
    public void setOrientation(String orient)
    {
	if (orient.equals("Positive"))
	    m_preferences.put("gui-board-orientation", "positive");
	else if (orient.equals("Negative"))
	    m_preferences.put("gui-board-orientation", "negative");
	else {
	    System.out.println("GuiBoard: unknown orientation '" + 
			       orient + "'.");
	}
        repaint();
    }

    public void updateRotation(int amount)
    {
        int rot = m_preferences.getInt("gui-board-rotation");
        rot = (rot + amount + 12) % 12;
        m_preferences.put("gui-board-rotation", rot);
    }

    public void setRotation(int rot)
    {
        m_preferences.put("gui-board-rotation", rot);
    }

    public void initSize(int w, int h)
    {
        initSize(m_mode, w, h);
    }

    /** Creates a board of the given dimensions.
        Dirty flag is set to false. 
        @param m type of board to create (HEX or Y)
	@param w width of the board in cells
	@param h height of the board in cells
    */
    private void initSize(int m, int w, int h)
    {
	System.out.println("GuiBoard.initSize: " 
                           + (m == HEXBOARD ? "(HEX) " : "(Y) ") 
                           + w + " " + h);

        m_mode = m;
	m_width = w; 
	m_height = h;
	m_size = new Dimension(m_width, m_height);
        
        m_dirty_stones = false;
        clearArrows();

        if (m_mode == HEXBOARD) 
        {
            m_field = new GuiField[w*h];
            for (int x=0; x<w*h; x++) {
                m_field[x] = new GuiField(HexPoint.get(x % w, x / w));
                m_field[x].setAttributes(GuiField.DRAW_CELL_OUTLINE);
            }
        } 
        else 
        {
            int n = w*(w+1)/2;
            m_field = new GuiField[n];
            for (int y=0,i=0; y<w; y++) {
                for (int x=0; x<=y; x++,i++) {
                    m_field[i] = new GuiField(HexPoint.get(x, y));
                    m_field[i].setAttributes(GuiField.DRAW_CELL_OUTLINE);
                }
            }
        }
	clearAll();
        repaint();
    }

    /** Creates a board with the given dimensions.
	Convenience function.  
	@param dim dimension of the board
	@see initSize(int, int)
    */
    public void initSize(Dimension dim)
    {
	initSize(m_mode, dim.width, dim.height);
    }

    /** Gets the size of the board.
	@return size of the board as a Dimension.
    */
    public Dimension getBoardSize()
    {
	return m_size;
    }

    public boolean isHexBoard()
    {
        return m_mode == HEXBOARD;
    }

    public boolean isYBoard() 
    {
        return m_mode == YBOARD;
    }

    /** Clears all marks and stones from the board. */
    public void clearAll()
    {
	for (int x=0; x<m_field.length; x++) {
	    m_field[x].clear();
        }
        repaint();
    }

    /** Makes a copy of the current fields if the dirty flag is not
        already set, and then sets the dirty flag to true. See
        clearMarks().
    */
    public void aboutToDirtyStones()
    {
        if (!m_dirty_stones) {
            m_backup_field = new GuiField[m_field.length];
            for (int i=0; i<m_field.length; i++) 
                m_backup_field[i] = new GuiField(m_field[i]);
        }
        m_dirty_stones = true;
    }

    public boolean areStonesDirty()
    {
        return m_dirty_stones;
    }

    /** Adds an arrow. */
    public void addArrow(HexPoint from, HexPoint to)
    {
        m_arrows.add(new Pair<HexPoint, HexPoint>(from, to));
        repaint();
    }

    public void clearArrows()
    {
        m_arrows.clear();
        repaint();
    }

    /** Clears dynamic marks, leaving stones intact. If the dirty flag is set,
        revert the fields to the saved fields saved in markStonesDirty().
        Dirty stones flag is set to false. See aboutToDirtyStones().
        Empties the list of arrows. 
     */
    public void clearMarks()
    {
        if (m_dirty_stones) {
            for (int i=0; i<m_field.length; i++) {
                m_field[i] = new GuiField(m_backup_field[i]);
            }
        }
        m_dirty_stones = false;
        
        clearArrows();
        
	for (int x=0; x<m_field.length; x++) {
	    m_field[x].clearAttributes(GuiField.LAST_PLAYED | 
                                       GuiField.SWAP_PLAYED | 
                                       GuiField.DRAW_TEXT | 
                                       GuiField.DRAW_ALPHA);
        }
        repaint();
    }

    /** Sets the given point to the given color.
        Special points are ignored (SWAP_SIDES, RESIGN, etc).
	@param point the point
	@param color the color to set it to.
    */
    public void setColor(HexPoint point, HexColor color)
    {
	GuiField f = getField(point);
        if (f != null) {
            f.setColor(color);
            repaint();
        }
    }

    /** Gets the color of the specified point.
	@param point the point whose color we with to obtain.
	@return the color of <code>point</code>
    */
    public HexColor getColor(HexPoint point)
    {
	GuiField f = getField(point);
	return f.getColor();
    }

    /** Gets the field at the specified point. 
        Special points are ignored (SWAP_SIDES, etc).
    */
    public GuiField getField(HexPoint point)
    {
        if (point == HexPoint.SWAP_SIDES
            || point == HexPoint.SWAP_PIECES
            || point == HexPoint.PASS
            || point == HexPoint.RESIGN
            || point == HexPoint.FORFEIT) {
            return null;
        }

	for (int x=0; x<m_field.length; x++) {
	    if (m_field[x].getPoint() == point) 
		return m_field[x];
        }
	assert(false);
	return null;
    }

    /** Marks the given point to show which move was played last, or
        clears the mark if <code>point</code> is <code>null</code>. */

    public void markLastPlayed(HexPoint point)
    {
        assert(point != HexPoint.SWAP_SIDES && point != HexPoint.SWAP_PIECES);

	if (m_last_played != null) {
	    m_last_played.clearAttributes(GuiField.LAST_PLAYED);
            m_last_played = null;
        }
	if (point != null) {
	    m_last_played = getField(point);
            if (m_last_played != null) {
                m_last_played.setAttributes(GuiField.LAST_PLAYED);
            }
	}
        repaint();
    }

    /** Clear swap marks */
    public void clearSwapPlayed()
    {
        for (int x=0; x<m_field.length; x++) {
            m_field[x].clearAttributes(GuiField.SWAP_PLAYED);
        }
        repaint();
    }
        
    /** Add swap mark to all pieces on the board (hopefully there is
     * exactly one of them */
    public void markSwapPlayed()
    {
        for (int x=0; x<m_field.length; x++) {
            HexPoint p = m_field[x].getPoint();
            if (p.is_cell() && m_field[x].getColor() != HexColor.EMPTY) {
                m_field[x].setAttributes(GuiField.SWAP_PLAYED);
            }
        }
        repaint();
    }

    /** Sets the given point's alpha color. */
    public void setAlphaColor(HexPoint point, Color color)
    {
        GuiField f = getField(point);
        if (f != null) {
            f.setAlphaColor(color);
            repaint();
        }
    }

    public void setAlphaColor(HexPoint point, Color color, float blend)
    {
        GuiField f = getField(point);
        if (f != null) {
            f.setAlphaColor(color, blend);
            repaint();
        }
    }

    /** Returns the point's alpha color; null if it is 'swap-sides'
        or resign or similar. */
    public Color getAlphaColor(HexPoint point)
    {
        GuiField f = getField(point);
        if (f != null) {
            return f.getAlphaColor();
        } else {
            return null;
        }
    }
    
    /** Sets the given point's text. */
    public void setText(HexPoint point, String str)
    {
        getField(point).setText(str);
        repaint();
    }

    /** Sets whether this cell is selected. */
    public void setSelected(HexPoint point, boolean selected)
    {
        getField(point).setSelected(selected);
        repaint();
    }

    /** Check if the board is full */
    public boolean isBoardFull()
    {
        for (int x=0; x<m_field.length; x++) {
            if (m_field[x].getColor() == HexColor.EMPTY)
                return false;
        }
        return true;
    }

    /** Count the number of pieces on the board */
    public int numberOfPieces()
    {
        int count = 0;
        for (int x=0; x<m_field.length; x++) {
            HexPoint point = m_field[x].getPoint();
            if (m_field[x].getColor() != HexColor.EMPTY) {
                count++;
            }
        }
        return count;
    }
    
    
    /** Change the pieces' colors without moving them. This is only
        used in Y. */
    public void swapColors() 
    {
        for (int x=0; x<m_field.length; x++) {
            HexPoint point = m_field[x].getPoint();
            HexColor color = m_field[x].getColor();
            m_field[x].setColor(color.otherColor());
        }
    }

    /** Change the pieces' colors and move them. This is only used in
        Hex. */
    public void swapPieces() 
    {
        // Due to the weird way the data structures are set up, it
        // is tricky to move pieces to another location on the board.
        // In particular, there is no O(1) way to find the HexField
        // attached to a given HexPoint.
        Map<HexPoint, HexColor> colors = new TreeMap<HexPoint, HexColor>();
        for (int x=0; x<m_field.length; x++) {
            HexPoint point = m_field[x].getPoint();
            colors.put(point, m_field[x].getColor());
        }
        for (int x=0; x<m_field.length; x++) {
            HexPoint point = m_field[x].getPoint();
            HexPoint otherpoint = point.reflect();
            m_field[x].setColor(colors.get(otherpoint).otherColor());
        }
    }

    /** Stores the current state as a setup position in the
        given sgf node. */
    public void storePosition(Node node)
    {
        for (int x=0; x<m_field.length; x++) {
            HexPoint point = m_field[x].getPoint();
            HexColor color = m_field[x].getColor();
            if (color == HexColor.EMPTY)
                continue;

            node.addSetup(color, point);
        }
    }

    public void paintImmediately()
    {
        assert SwingUtilities.isEventDispatchThread();
	super.paintImmediately(0, 0, getWidth(), getHeight());
    }

    /** Displays this vc on the board. */
    public void displayVC(VC vc)
    {
        getField(vc.getFrom()).setAlphaColor(Color.blue);
        getField(vc.getTo()).setAlphaColor(Color.blue);
        
        Vector<HexPoint> carrier = vc.getCarrier();
        for (int i=0; i<carrier.size(); i++) 
            getField(carrier.get(i)).setAlphaColor(Color.green);

        Vector<HexPoint> stones = vc.getStones();
        for (int i=0; i<stones.size(); i++) 
            getField(stones.get(i)).setAlphaColor(Color.red);

        Vector<HexPoint> key = vc.getKey();
        for (int i=0; i<key.size(); i++)
            getField(key.get(i)).setAlphaColor(Color.yellow);
    }

    //------------------------------------------------------------

    public int print(Graphics g, PageFormat format, int page)
        throws PrinterException
    {
        if (page >= 1)
        {
            return Printable.NO_SUCH_PAGE;
        }
        double width = getWidth();
        double height = getHeight();
        double pageWidth = format.getImageableWidth();
        double pageHeight = format.getImageableHeight();
        double scale = 1;
        if (width >= pageWidth)
            scale = pageWidth / width;
        double xSpace = (pageWidth - width * scale) / 2;
        double ySpace = (pageHeight - height * scale) / 2;
        Graphics2D g2d = (Graphics2D)g;
        g2d.translate(format.getImageableX() + xSpace,
                      format.getImageableY() + ySpace);
        g2d.scale(scale, scale);
        print(g2d);
        return Printable.PAGE_EXISTS;
    }

    //------------------------------------------------------------

    /** Converts a hex string representing a bitset into a vector of
        HexPoints.  This relies on m_field being ordered in a particular
        fashion.
        
        NOTE: THIS IS BROKEN SINCE HexPoint was changed in wolve, r182. 
              USE BASE 64 INSTEAD!

        FIXME: switch carriers to be printed as a list of HexPoints instead of 
        as hex strings?
    */
    private Vector<HexPoint> convertHexString(String str)
    {
        Vector<HexPoint> ret = new Vector<HexPoint>();

        for (int i=0; i<str.length(); i++) {
            BigInteger big = new BigInteger(StringUtils.reverse(str), 16);
            for (int j=0; j<m_field.length; j++) {
                if (big.testBit(j))
                    ret.add(m_field[j].getPoint());
            }
        }

        return ret;
    }


    /** Converts a base 64 string representing a bitset into a vector of
        HexPoints.  
    */
    private static final String m_base64 
      = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/";

    private Vector<HexPoint> convertBase64String(String str)
    {

        Vector<HexPoint> ret = new Vector<HexPoint>();
        for (int i=0; i<str.length(); i++) {
            int v = m_base64.indexOf(str.charAt(i));
            assert(v != -1);
            for (int j=0; j<6 && i*6 + j < HexPoint.MAX_POINTS; j++) {
                if ((v & (1 << j)) != 0)
                    ret.add(HexPoint.get(i*6 + j));
            }
        }
        return ret;
    }

    private GuiField[] flipFields(GuiField field[])
    {
	GuiField out[] = new GuiField[field.length];
	for (int i=0; i<field.length; i++) {
	    HexPoint p = field[i].getPoint();
	    out[i] = new GuiField(field[i]);
            out[i].setPoint(HexPoint.get(p.y, p.x));
	}
	return out;
    }

    private class BoardPanel
	extends JPanel
    {
	public BoardPanel()
	{
	    setFocusable(true);
	}

	public void paintComponent(Graphics graphics)
	{
	    int w = getWidth();
	    int h = getHeight();

	    if (m_image == null) {
		m_image = createImage(w, h);
	    }

	    int bw = m_width;
	    int bh = m_height;
	    GuiField ff[] = m_field;
            Vector<Pair<HexPoint, HexPoint>> arrows = m_arrows;

            boolean mirrored = false;
            if (m_preferences.get("gui-board-orientation").equals("negative")) {
                mirrored = true;
            }

            int rotation = m_preferences.getInt("gui-board-rotation");
            
	    m_drawer.draw((Graphics2D)m_image.getGraphics(), 
                          w, h, bw, bh, rotation, mirrored, 
                          ff, arrows);
	    graphics.drawImage(m_image, 0, 0, null);
	}

	public void setBounds(int x, int y, int w, int h)
	{
	    super.setBounds(x, y, w, h);
	    m_image = null;
	}
    }

    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    private int m_width, m_height;
    private Dimension m_size;
    private int m_mode;

    private Image m_image;
    private GuiField m_field[];
    private Vector<Pair<HexPoint, HexPoint>> m_arrows;

    private boolean m_dirty_stones;
    private GuiField m_backup_field[];

    private GuiField m_last_played;

    private BoardDrawerBase m_drawer;
    private BoardPanel m_boardPanel;

    private Listener m_listener;
    private GuiPreferences m_preferences;
}

//----------------------------------------------------------------------------
