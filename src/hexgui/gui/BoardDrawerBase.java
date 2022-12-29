//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import hexgui.util.Pair;
import hexgui.util.RadialGradientPaint;
import hexgui.hex.HexColor;
import hexgui.hex.HexPoint;

import java.util.Vector;
import javax.swing.*;

import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.Image;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.BasicStroke;

import java.awt.event.*;
import java.net.URL;

//----------------------------------------------------------------------------

/** Base class for board drawing.

    <p>Board drawers are responsible for drawing the background,
    labels, field outlines, and stone shadows.  In addition, they
    are also responsible for determining the actual position of each
    field in the window.  Field contents (i.e. stones, markers,
    numerical values, etc) are not drawn, they are drawn with the
    GuiField class.

    <p>Board sizes supported are <code>m x n</code> where
    <code>m</code> and <code>n</code> range from 1 to 31.  By
    default, black connects top and bottom and should be labeled with
    letters.  White connects left and right and should be labeled with
    numbers.
*/
public abstract class BoardDrawerBase
{
    public BoardDrawerBase()
    {
	m_background = null;
    }

    /** Loads the image in <code>filename</code> and sets it as the
	background.  If <code>filename</code> does not exist no
	background image is displayed.  Image will be scaled to fit
	the window.
	@param filename filename of the image to use as a background.
    */
    public void loadBackground(String filename)
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource(filename);
        if (url == null) {
	    System.out.println("loadBackground: could not load '" + 
			       filename + "'!");
            m_background = null;            
	} else {
	    m_background = new ImageIcon(url).getImage();
	}
    }

    /** Calculates and sets the geometry of the board. */
    public void setGeometry(int w, int h, int bw, int bh, double rotation, boolean mirrored)
    {
        // Some fixed parameters. If borderradius = 1, the border just
        // touches the outermost cells. If excentricity = 0, the
        // corner circles are centered at the centers of the corner
        // cells.
        m_excentricity_acute = 0.5;
        m_excentricity_obtuse = 0.5;
        m_borderradius = 1.2;
        m_margin = 0.6;
        m_labelradius = 1.1;
        m_strokewidth = 0.03;

        m_width = w;
        m_height = h;
        m_bwidth = bw;
        m_bheight = bh;
        m_rotation = rotation;
        m_mirrored = mirrored;

        // Intermediate coordinates. Here a1 = (0,0), and the distance
        // between the centers of adjacent cells is 1. This is used to
        // calculate the general orientation of the board before
        // taking into account margins, centering, or the fact that
        // Java's coordinate system is upside down. (In the
        // intermediate coordinates, the y-axis points upwards).
        double file_angle;
        double rank_angle;
        if (mirrored) {
            file_angle = Math.PI/6 * (8 - rotation);
            rank_angle = Math.PI/6 * (10 - rotation);
        } else {
            file_angle = Math.PI/6 * (10 - rotation);
            rank_angle = Math.PI/6 * (8 - rotation);
        }        
        double dfX = Math.cos(file_angle);
        double dfY = Math.sin(file_angle);
        double drX = Math.cos(rank_angle);
        double drY = Math.sin(rank_angle);

        // Calculate the centers and radii of the four corner circles.
        double r0 = (m_borderradius - m_excentricity_acute/2) / Math.sqrt(3);
        double p0X = -1.0/3 * m_excentricity_acute * dfX - 1.0/3 * m_excentricity_acute * drX;
        double p0Y = -1.0/3 * m_excentricity_acute * dfY - 1.0/3 * m_excentricity_acute * drY;
        double p2X = (bw - 1 + 1.0/3 * m_excentricity_acute) * dfX + (bh - 1 + 1.0/3 * m_excentricity_acute) * drX;
        double p2Y = (bw - 1 + 1.0/3 * m_excentricity_acute) * dfY + (bh - 1 + 1.0/3 * m_excentricity_acute) * drY;

        double r1 = (m_borderradius - m_excentricity_obtuse/2) / Math.sqrt(3);
        double p1X = (bw - 1 + 1.0/3 * m_excentricity_acute) * dfX - 1.0/3 * m_excentricity_acute * drX;
        double p1Y = (bw - 1 + 1.0/3 * m_excentricity_acute) * dfY - 1.0/3 * m_excentricity_acute * drY;
        double p3X = -1.0/3 * m_excentricity_acute * dfX + (bh - 1 + 1.0/3 * m_excentricity_acute) * drX;
        double p3Y = -1.0/3 * m_excentricity_acute * dfY + (bh - 1 + 1.0/3 * m_excentricity_acute) * drY;

        // Bounding box of the board, including borders.
        double minX = Math.min(Math.min(p0X-r0, p1X-r1), Math.min(p2X-r0, p3X-r1));
        double maxX = Math.max(Math.max(p0X+r0, p1X+r1), Math.max(p2X+r0, p3X+r1));
        double minY = Math.min(Math.min(p0Y-r0, p1Y-r1), Math.min(p2Y-r0, p3Y-r1));
        double maxY = Math.max(Math.max(p0Y+r0, p1Y+r1), Math.max(p2Y+r0, p3Y+r1));

        // Scaling factor.
        double scaleX = w / (maxX - minX + 2*m_margin);
        double scaleY = h / (maxY - minY + 2*m_margin);
        double scale = Math.min(scaleX, scaleY);

        // Geometry.
        m_dfileX = dfX * scale;
        m_dfileY = -dfY * scale;
        m_drankX = drX * scale;
        m_drankY = -drY * scale;
        m_originX = w/2.0 - scale * (minX + maxX)/2;
        m_originY = h/2.0 + scale * (minY + maxY)/2;
        m_fieldSize = scale;
    }
    
    /** Gets the field containing the specified point.
	NOTE: uses the position of fields from the last call to draw().
	Also assumes the set of fields given are the same as those in the
	last call to draw(). 
	@param p the point
	@param field the set of fields to search through.
	@return the field in the set that p is in or <code>null</code> if
                p is not in any field.
    */
    public GuiField getFieldContaining(Point p, GuiField field[])
    {
	if (m_outlines == null)
	    return null;
	for (int x=0; x<field.length; x++) {
	    if (m_outlines[x].contains(p)) 
		return field[x];
	}
	return null;
    }

    /** Draws the board.
	The size of the region to draw to, the size of the board, and the
	field to draw must be given.  The position of each field is 
	then calculated and the board drawn. 
	@param g graphics context to draw to
	@param w the width of the region to draw in
	@param h the height of the region to draw in
	@param bw the width of the board (in fields)
	@param bh the height of the board (in fields)
	@param mirrored true if board is mirrored
        @param field the fields to draw
        @param arrows the list of arrows to draw
    */
    public void draw(Graphics2D g, 
		     int w, int h, int bw, int bh,
                     double rotation,
		     boolean mirrored,
		     GuiField field[],
                     Vector<Pair<HexPoint, HexPoint>> arrows)
    {
        setGeometry(w, h, bw, bh, rotation-0.1, mirrored); // ###
        
	m_outlines = calcCellOutlines(field);

	setAntiAliasing(g);
	drawBackground(g);
        drawEdges(g);
	drawCells(g, field);
	drawLabels(g);
	drawShadows(g, field);
	drawFields(g, field);
        drawAlpha(g, field);
        drawArrows(g, arrows);
    }

    //------------------------------------------------------------

    /** Calculate an array of hexagons representing the board's cells.
	@param the fields it will need to draw
     */
    protected Path2D[] calcCellOutlines(GuiField field[])
    {
	Path2D outline[] = new Path2D[field.length];
        for (int x = 0; x < outline.length; x++) {
            HexPoint c = field[x].getPoint();
            Point2D.Double p;
            outline[x] = new Path2D.Double();
            moveToHexPoint(outline[x], c.x, c.y, 1, 0, 0, 0, 0);
            lineToHexPoint(outline[x], c.x, c.y, 0, 1, 0, 0, 0);
            lineToHexPoint(outline[x], c.x, c.y, 0, 0, 1, 0, 0);
            lineToHexPoint(outline[x], c.x, c.y, -1, 0, 0, 0, 0);
            lineToHexPoint(outline[x], c.x, c.y, 0, -1, 0, 0, 0);
            lineToHexPoint(outline[x], c.x, c.y, 0, 0, -1, 0, 0);
            outline[x].closePath();
        }	
	return outline;
    }
    
    /** Draws the outlines of the given fields. 
	@param g graphics context to draw to.
	@param field the list of fields to draw.
    */
    protected void drawCells(Graphics2D g, GuiField field[])
    {
        g.setStroke(new BasicStroke((float)(m_strokewidth * m_fieldSize)));

	g.setColor(Color.black);
	for (int i=0; i<m_outlines.length; i++) {
	    if ((field[i].getAttributes() & GuiField.DRAW_CELL_OUTLINE) != 0) {
		g.draw(m_outlines[i]);
	    }
	}

	g.setColor(Color.yellow);
	for (int i=0; i<m_outlines.length; i++) {
	    if ((field[i].getAttributes() & GuiField.SELECTED) != 0) {
		g.draw(m_outlines[i]);
	    }
	}
    }

    /** An auxiliary function for finding a point with the given Hex
     coordinates. Here a is the file, b is the rank, c, d, e are
     offsets, and r, theta is a polar coordinate. */
    protected Point2D.Double hexPoint(double a, double b, double c, double d, double e, double r, double theta)
    {
        double xr = r * Math.cos(Math.PI * theta / 180) * Math.sqrt(3);
        double yr = r * Math.sin(Math.PI * theta / 180);

        a += (2*c + 1*d - e + yr + xr)/3;
        b += (-c + 1*d + 2*e - 2*yr)/3;

        double x = m_originX + m_dfileX * a + m_drankX * b;
        double y = m_originY + m_dfileY * a + m_drankY * b;

        return new Point2D.Double(x, y);
    }
    
    /** An auxiliary function for adding a point to a polygon, using
     Hex coordinates. Here a is the file, b is the rank, c, d, e
     are offsets, and r, theta is a polar coordinate. */
    protected void addHexPoint(Polygon p, double a, double b, double c, double d, double e, double r, double theta)
    {
        Point2D.Double point = hexPoint(a, b, c, d, e, r, theta);
        p.addPoint((int)point.x, (int)point.y);
    }

    /** An auxiliary function for adding a point to a path, using
     Hex coordinates. Here a is the file, b is the rank, c, d, e
     are offsets, and r, theta is a polar coordinate. */
    protected void moveToHexPoint(Path2D p, double a, double b, double c, double d, double e, double r, double theta)
    {
        Point2D.Double point = hexPoint(a, b, c, d, e, r, theta);
        p.moveTo(point.x, point.y);
    }

    /** An auxiliary function for adding a point to a path, using
     Hex coordinates. Here a is the file, b is the rank, c, d, e
     are offsets, and r, theta is a polar coordinate. */
    protected void lineToHexPoint(Path2D p, double a, double b, double c, double d, double e, double r, double theta)
    {
        Point2D.Double point = hexPoint(a, b, c, d, e, r, theta);
        p.lineTo(point.x, point.y);
    }

    protected Point getLocation(HexPoint p)
    {
        Point2D.Double pp = hexPoint(p.x, p.y, 0, 0, 0, 0, 0);
        return new Point((int)pp.x, (int)pp.y);
    }

    /** Draws the board, according to the current geometry, which must
        have been set with setGeometry.
        @param g graphics context to draw to.
    */
    protected void drawEdges(Graphics2D g)
    {
        // 1-edge.

        double r0 = m_borderradius - m_excentricity_acute/2;
        double r1 = m_borderradius - m_excentricity_obtuse/2;
        
        Path2D e1 = new Path2D.Double();
        moveToHexPoint(e1, 0, 0, 0, -m_excentricity_acute, 0, r0, 90);
        for (int theta = 90; theta <= 150; theta += 10) {
            lineToHexPoint(e1, 0, 0, 0, -m_excentricity_acute, 0, r0, theta);
        }
        for (int a=0; a<m_bwidth; a++) {
            lineToHexPoint(e1, a, 0, 0, -1, 0, 0, 0);
            lineToHexPoint(e1, a, 0, 0, 0, -1, 0, 0);
        }
        lineToHexPoint(e1, m_bwidth-1, 0, 0.5, 0, -0.5, 0, 0);
        for (int theta = 60; theta <= 90; theta += 10) {
            lineToHexPoint(e1, m_bwidth-1, 0, m_excentricity_obtuse/3, 0, -m_excentricity_obtuse/3, r1, theta);
        }
        e1.closePath();
            
        Path2D e2 = new Path2D.Double();
        moveToHexPoint(e2, 0, 0, 0, -m_excentricity_acute, 0, r0, 210);
        for (int theta = 210; theta >= 150; theta -= 10) {
            lineToHexPoint(e2, 0, 0, 0, -m_excentricity_acute, 0, r0, theta);
        }
        for (int b=0; b<m_bheight; b++) {
            lineToHexPoint(e2, 0, b, 0, -1, 0, 0, 0);
            lineToHexPoint(e2, 0, b, -1, 0, 0, 0, 0);
        }
        lineToHexPoint(e2, 0, m_bheight-1, -0.5, 0, 0.5, 0, 0);
        for (int theta = 240; theta >= 210; theta -= 10) {
            lineToHexPoint(e2, 0, m_bheight-1, -m_excentricity_obtuse/3, 0, m_excentricity_obtuse/3, r1, theta);
        }
        e2.closePath();
            
        Path2D e3 = new Path2D.Double();
        moveToHexPoint(e3, m_bwidth-1, m_bheight-1, 0, m_excentricity_acute, 0, r0, -90);
        for (int theta = -90; theta <= -30; theta += 10) {
            lineToHexPoint(e3, m_bwidth-1, m_bheight-1, 0, m_excentricity_acute, 0, r0, theta);
        }
        for (int a=m_bwidth-1; a >= 0; a--) {
            lineToHexPoint(e3, a, m_bheight-1, 0, 1, 0, 0, 0);
            lineToHexPoint(e3, a, m_bheight-1, 0, 0, 1, 0, 0);
        }
        lineToHexPoint(e3, 0, m_bheight-1, -0.5, 0, 0.5, 0, 0);
        for (int theta = -120; theta <= -90; theta += 10) {
            lineToHexPoint(e3, 0, m_bheight-1, -m_excentricity_obtuse/3, 0, m_excentricity_obtuse/3, r1, theta);
        }
        e3.closePath();
            
        Path2D e4 = new Path2D.Double();
        moveToHexPoint(e4, m_bwidth-1, m_bheight-1, 0, m_excentricity_acute, 0, r0, 30);
        for (int theta = 30; theta >= -30; theta -= 10) {
            lineToHexPoint(e4, m_bwidth-1, m_bheight-1, 0, m_excentricity_acute, 0, r0, theta);
        }
        for (int b=m_bheight-1; b >= 0; b--) {
            lineToHexPoint(e4, m_bwidth-1, b, 0, 1, 0, 0, 0);
            lineToHexPoint(e4, m_bwidth-1, b, 1, 0, 0, 0, 0);
        }
        lineToHexPoint(e4, m_bwidth-1, 0, 0.5, 0, -0.5, 0, 0);
        for (int theta = 60; theta >= 30; theta -= 10) {
            lineToHexPoint(e4, m_bwidth-1, 0, m_excentricity_obtuse/3, 0, -m_excentricity_obtuse/3, r1, theta);
        }
        e4.closePath();

	g.setColor(Color.black);
        g.fill(e1);
        g.fill(e3);
        
	g.setColor(Color.white);
        g.fill(e2);
        g.fill(e4);

        g.setStroke(new BasicStroke((float)(m_strokewidth * m_fieldSize)));
        g.setColor(Color.black);
        g.draw(e1);
        g.draw(e2);
        g.draw(e3);
        g.draw(e4);
    }

    //------------------------------------------------------------

    protected int getShadowOffset()
    {
        return (int)((m_fieldSize - 2*GuiField.getStoneMargin((int)m_fieldSize)) / 12);
    }

    protected void drawBackground(Graphics2D g)
    {
	if (m_background != null) 
	    g.drawImage(m_background, 0, 0, m_width, m_height, null);
    }

    protected void drawLabel(Graphics2D g, Point p, String string, int xoff)
    {
        double size = m_fieldSize * 0.4;
        Font f = g.getFont();
        Font f2 = f.deriveFont((float)size);

        FontMetrics fm = g.getFontMetrics(f2);
	int width = fm.stringWidth(string);
	int height = fm.getAscent();

        g.setFont(f2);
        int x = width/2;
	int y = (int)(0.9 * height/2);
	g.drawString(string, p.x + xoff - x, p.y + y); 
        g.setFont(f);
    }

    protected void drawLabels(Graphics2D g)
    {
        String string;
        Point2D.Double p;
        
        g.setColor(Color.black);

        for (int a=0; a<m_bwidth; a++) {
            string = Character.toString((char)((int)'A' + a));
            p = hexPoint(a, -m_labelradius, 0, 0, 0, 0, 0);
            drawLabel(g, new Point((int)p.x, (int)p.y), string, 0);
            p = hexPoint(a, m_bheight-1+m_labelradius, 0, 0, 0, 0, 0);
            drawLabel(g, new Point((int)p.x, (int)p.y), string, 0);
        }
        for (int b=0; b<m_bheight; b++) {
            string = Integer.toString(b+1);
            p = hexPoint(-m_labelradius, b, 0, 0, 0, 0, 0);
            drawLabel(g, new Point((int)p.x, (int)p.y), string, 0);
            p = hexPoint(m_bwidth-1+m_labelradius, b, 0, 0, 0, 0, 0);
            drawLabel(g, new Point((int)p.x, (int)p.y), string, 0);
        }
    }
    
    protected void drawShadows(Graphics2D graphics, GuiField[] field)
    {
        if (m_fieldSize <= 10)
            return;

        graphics.setComposite(COMPOSITE_3);

        // The following calculation is byzantine and should all be converted to double.
        // For now, we echo how the stone radius is determined in GuiField.
        int w = (int)m_fieldSize;
        int rad = w/2;
        int mar = GuiField.getStoneMargin(rad*2);
        int size = rad - mar;

        int offset = getShadowOffset();
        for (int pos = 0; pos < field.length; pos++) {
	    if (field[pos].getColor() == HexColor.EMPTY)
		continue;
	    Point location = getLocation(field[pos].getPoint());
	    graphics.setColor(Color.black);
	    graphics.fillOval(location.x - size + offset,
			      location.y - size + offset,
			      size*2, size*2);
	}
        graphics.setPaintMode();
    }

    protected void drawFields(Graphics2D g, GuiField field[])
    {
	for (int x=0; x<field.length; x++) {
            Point p = getLocation(field[x].getPoint());
	    field[x].draw(g, p.x, p.y, (int)m_fieldSize, (int)m_fieldSize);
	}
    }

    protected void drawAlpha(Graphics2D g, GuiField field[])
    {
        for (int i=0; i<m_outlines.length; i++) {
            if ((field[i].getAttributes() & GuiField.DRAW_ALPHA) == 0)
                continue;
            
            Color color = field[i].getAlphaColor();
            if (color == null)
                continue;
            
            g.setComposite(AlphaComposite.
                             getInstance(AlphaComposite.SRC_OVER, 
                                         field[i].getAlphaBlend()));
            
            g.setColor(color);
            g.fill(m_outlines[i]);
	}
    }

    protected void drawArrows(Graphics2D g, 
                              Vector<Pair<HexPoint,HexPoint>> arrows)
    {
        g.setColor(Color.BLUE);
        for (int i=0; i<arrows.size(); i++) {
            Point fm = getLocation(arrows.get(i).first);
            Point to = getLocation(arrows.get(i).second);
            drawArrow(g, fm.x, fm.y, to.x, to.y, 1.5);
        }
    }

    protected void setAntiAliasing(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
    }

    public static void drawArrow(Graphics2D g2d, int x1, int y1, 
                                 int x2, int y2, double stroke) 
    {
        double aDir=Math.atan2(x1-x2,y1-y2);
        g2d.drawLine(x2,y2,x1,y1);
	// make the arrow head solid even if dash pattern has been specified
        //g2d.setStroke(new BasicStroke(1f));

        Polygon tmpPoly=new Polygon();
        int i1=12+(int)(stroke*2);
        // make the arrow head the same size regardless of the length
        int i2=6+(int)stroke;		
        tmpPoly.addPoint(x2,y2); // arrow tip
        tmpPoly.addPoint(x2+xCor(i1,aDir+.5),y2+yCor(i1,aDir+.5));
        tmpPoly.addPoint(x2+xCor(i2,aDir),y2+yCor(i2,aDir));
        tmpPoly.addPoint(x2+xCor(i1,aDir-.5),y2+yCor(i1,aDir-.5));
        tmpPoly.addPoint(x2,y2); // arrow tip
        g2d.drawPolygon(tmpPoly);
        g2d.fillPolygon(tmpPoly); 
    }

    private static int yCor(int len, double dir) {return (int)(len * Math.cos(dir));}
    private static int xCor(int len, double dir) {return (int)(len * Math.sin(dir));}

    protected Image m_background;

    protected int m_width, m_height; // the width and height of the canvas
    protected int m_bwidth, m_bheight; // the width (files) and height (ranks) of the board

    protected double m_rotation; // clock direction of the a1 cell:
                                 // 9=left (diamond orientation),
                                 // 10=upper left (flat orientation).
    protected boolean m_mirrored; // mirror the board?

    // Fixed parameters.
    protected double m_excentricity_acute;
    protected double m_excentricity_obtuse;
    protected double m_borderradius;
    protected double m_margin;
    protected double m_labelradius;
    protected double m_strokewidth; 
    
    // Computed geometry of the board.
    protected double m_originX, m_originY;  // Location of the a1 cell.
    protected double m_dfileX, m_dfileY;      // Vector from a1 to b1.
    protected double m_drankX, m_drankY;      // Vector from a1 to a2.

    protected double m_fieldSize;   // for stone size, label size etc.
    
    // Cell outlines.
    protected Path2D m_outlines[];

    protected static final AlphaComposite COMPOSITE_3
        = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);

}

//----------------------------------------------------------------------------
