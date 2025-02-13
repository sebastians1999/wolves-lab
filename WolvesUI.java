import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;


@SuppressWarnings("serial")
public class WolvesUI extends JPanel {

	private int squaresize;
	private Wolves game;
	
	public WolvesUI(Wolves game, int squaresize) {
		
		this.game = game;
		game.attach(this);
		this.squaresize = squaresize;
		setPreferredSize(new Dimension(game.getNumbCols()*squaresize,game.getNumbRows()*squaresize));
	}

	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;
		
		//draw background
		drawgrid(g2);
		
		//draw alive cells
		g2.setColor(Color.DARK_GRAY);
		for (int i=0; i<game.getNumbCols(); i++)
			for (int j=0; j<game.getNumbRows(); j++) 
				if (game.isWolf(i,j)) {
				    //System.out.println("wolf: " +  game.grid[i][j]);
				        g2.setColor(Color.DARK_GRAY);
					g2.fill(new Rectangle2D.Double(i*squaresize+1,j*squaresize+1,squaresize-1,squaresize-1));
				} else if (game.isPrey(i,j)) {
					g2.setColor(Color.YELLOW);
					//System.out.println("Prey: " +  game.grid[i][j]);
					g2.fill(new Rectangle2D.Double(i*squaresize+1,j*squaresize+1,squaresize-1,squaresize-1));
				}

		//System.out.println("--------------");
	}

	public void drawgrid(Graphics2D g2) {
		//fillbackground
		g2.setColor(Color.LIGHT_GRAY);
		g2.fill(getVisibleRect());
		//drawgrid
		g2.setColor(Color.GRAY);
		for (int i=0;i<=game.getNumbCols();i++)
			g2.drawLine(i*squaresize, 0, i*squaresize,game.getNumbRows()*squaresize);
		for (int i=0;i<=game.getNumbRows();i++)
			g2.drawLine(0,i*squaresize,game.getNumbCols()*squaresize,i*squaresize);
	}

	public void update() {
		repaint();
	}
	
}
