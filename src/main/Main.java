package main;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.newdawn.slick.AppGameContainer;
import org.newdawn.slick.BasicGame;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Input;
import org.newdawn.slick.MouseListener;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.opengl.shader.ShaderProgram;

import visionCore.geom.Color;
import visionCore.math.Vec2f;
import visionCore.math.Vec2i;
import visionCore.util.Files;

public class Main extends BasicGame implements MouseListener {
	
	public static final int COLS = 256, ROWS = 128;
	//public static final int COLS = 8192, ROWS = 8192;
	
	public static String abspath;
	
	public static AppGameContainer game;
	
	
	public int ssboSrcId = -1, ssboDstId = -1, computeProgram = -1, computeShaderId = -1;
	public int workGroupSizeX, workGroupSizeY;
	
	public ShaderProgram cellShader;
	
	public Vec2f canvasSize, cam, cellSize;
	
	public float tickTime = 0, tickRate = 16;
	
	public boolean benchmark = false;
	public float savedTickRate;
	public int savedFPS;
	
	public boolean paused = true, showGrid = false;
	
	public Vec2i lastDrawCell, lastKillCell;
	
	
	public Main() {
		super("Conways Compute Shaders");
		
		this.canvasSize = new Vec2f();
		this.cam = new Vec2f();
		this.cellSize = new Vec2f();
		
		this.lastDrawCell = new Vec2i(-1, -1);
		this.lastKillCell = new Vec2i(-1, -1);
	}
	
	
	public static void main(String[] args) throws SlickException {
		
		setAbspath();
		
		AppGameContainer game = new AppGameContainer(new Main());
		Main.game = game;
		
		game.setDisplayMode(1024, 610, false);
		//game.setDisplayMode(512, 512, false);
		game.setAlwaysRender(true);
		game.setVSync(false);
		//game.setShowFPS(false);
		game.setTargetFrameRate(60);
		game.setForceExit(false);
		game.setResizable(true);
		
		game.start();
	}
	
	
	@Override
	public void init(GameContainer gc) throws SlickException {
		
		IntBuffer srcBuffer = BufferUtils.createIntBuffer(COLS * ROWS);
		IntBuffer dstBuffer = null;
		
		for (int i = 0; i < COLS * ROWS; ++i) { srcBuffer.put(0); }
		
		// Adds a glider to the top-left and a blinker somewhere at the top
		/*
		{
			srcBuffer.position(0);
			srcBuffer.put(0).put(1).put(0);
			
			srcBuffer.position(COLS * 1);
			srcBuffer.put(0).put(0).put(1);
			
			srcBuffer.position(COLS * 1 + COLS - 23);
			srcBuffer.put(1).put(1).put(1);
			
			srcBuffer.position(COLS * 2);
			srcBuffer.put(1).put(1).put(1);
		}
		*/
		
		// Adds a glider-gun to the top left
		{
			int x = 34, y = 0;
			
			srcBuffer.position(COLS * (y + 5) + x + 1).put(1).put(1);
			srcBuffer.position(COLS * (y + 6) + x + 1).put(1).put(1);
			
			srcBuffer.position(COLS * (y + 3) + x + 35).put(1).put(1);
			srcBuffer.position(COLS * (y + 4) + x + 35).put(1).put(1);
			
			srcBuffer.position(COLS * (y + 3) + x + 13).put(1).put(1);
			srcBuffer.position(COLS * (y + 4) + x + 12).put(1).put(0).put(0).put(0).put(1);
			srcBuffer.position(COLS * (y + 5) + x + 11).put(1).put(0).put(0).put(0).put(0).put(0).put(1);
			srcBuffer.position(COLS * (y + 6) + x + 11).put(1).put(0).put(0).put(0).put(1).put(0).put(1).put(1);
			srcBuffer.position(COLS * (y + 7) + x + 11).put(1).put(0).put(0).put(0).put(0).put(0).put(1);
			srcBuffer.position(COLS * (y + 8) + x + 12).put(1).put(0).put(0).put(0).put(1);
			srcBuffer.position(COLS * (y + 9) + x + 13).put(1).put(1);
			
			srcBuffer.position(COLS * (y + 1) + x + 25).put(1);
			srcBuffer.position(COLS * (y + 2) + x + 23).put(1).put(0).put(1);
			srcBuffer.position(COLS * (y + 3) + x + 21).put(1).put(1);
			srcBuffer.position(COLS * (y + 4) + x + 21).put(1).put(1);
			srcBuffer.position(COLS * (y + 5) + x + 21).put(1).put(1);
			srcBuffer.position(COLS * (y + 6) + x + 23).put(1).put(0).put(1);
			srcBuffer.position(COLS * (y + 7) + x + 25).put(1);
		}
		
		// Fills the canvas with blinkers
		/*
		{
			srcBuffer.rewind();
			
			for (int i = 0; i < ROWS / 5; ++i) {
				
				for (int j = 0; j < COLS * 2; ++j) { srcBuffer.put(0); }
				
				for (int j = 0; j < COLS / 5; ++j) { srcBuffer.put(0).put(1).put(1).put(1).put(0); }
				for (int j = 0; j < COLS - ((COLS / 5) * 5); ++j) { srcBuffer.put(0); }
				
				for (int j = 0; j < COLS * 2; ++j) { srcBuffer.put(0); } 
			}
			for (int i = 0; i < (ROWS - ((ROWS / 5) * 5)) * COLS; ++i) { srcBuffer.put(0); }
		}
		*/
		
		srcBuffer.position(srcBuffer.limit());
		srcBuffer.flip();
		
		dstBuffer = srcBuffer.duplicate();
		
		ssboSrcId = GL15.glGenBuffers();
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboSrcId);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, srcBuffer, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
		
		ssboDstId = GL15.glGenBuffers();
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboDstId);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, dstBuffer, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
		
		computeProgram = GL20.glCreateProgram();
		
		try { computeShaderId = createShader(abspath+"/shaders/GameOfLife.glsl", GL43.GL_COMPUTE_SHADER); } 
		catch (Exception e) { e.printStackTrace(); GL20.glDeleteProgram(computeProgram); }
		
		GL20.glAttachShader(computeProgram, computeShaderId);
		GL20.glLinkProgram(computeProgram);
		
		{
			int linked = GL20.glGetProgrami(computeProgram, GL20.GL_LINK_STATUS);
	        String programLog = GL20.glGetProgramInfoLog(computeProgram, 1000);
	        if (programLog.trim().length() > 0) {
	            System.err.println(programLog);
	        }
	        if (linked == 0) {
	            throw new AssertionError("Could not link program");
	        }
		}
		
		GL20.glUseProgram(computeProgram);
		
		IntBuffer workGroupSize = BufferUtils.createIntBuffer(3);
		GL20.glGetProgram(computeProgram, GL43.GL_COMPUTE_WORK_GROUP_SIZE, workGroupSize);
		workGroupSizeX = workGroupSize.get(0);
		workGroupSizeY = workGroupSize.get(1);
		
		GL20.glUseProgram(0);
		
		cellShader = ShaderProgram.loadProgram("shaders/Cells.vert", "shaders/Cells.frag");
		
		gc.getInput().addMouseListener(this);
	}
	

	@Override
	public void update(GameContainer gc, int delta) throws SlickException {
		
		if (Display.getWidth() / (float)COLS > Display.getHeight() / (float)ROWS) {
			
			canvasSize.x = (int)(Display.getHeight() * ((float)COLS / (float)ROWS));
			canvasSize.y = Display.getHeight();
			
		} else if (Display.getWidth() / (float)COLS < Display.getHeight() / (float)ROWS) {
			
			canvasSize.x = Display.getWidth();
			canvasSize.y = (int)(Display.getWidth() * ((float)ROWS / (float)COLS));
			
		} else { canvasSize.set(Display.getWidth(), Display.getHeight()); }
		
		cam.x = (Display.getWidth() - canvasSize.x) / 2;
		cam.y = (Display.getHeight() - canvasSize.y) / 2;
		cellSize.set(canvasSize.x / (float)COLS, canvasSize.y / (float)ROWS);
		
		if (gc.getInput().isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)) {
			
			draw(gc.getInput().getMouseX(), gc.getInput().getMouseY(), 0);
			
		} else if (gc.getInput().isMouseButtonDown(Input.MOUSE_RIGHT_BUTTON)) {
			
			draw(gc.getInput().getMouseX(), gc.getInput().getMouseY(), 1);
		}
		
		if (!paused) {
		
			if (tickRate > 0) {
			
				while (tickTime >= 1000f / tickRate) {
					
					tick();
					tickTime -= 1000f / tickRate;
				}
				
				tickTime += delta;
				
			} else { tick(); }
		}
	}
	
	private void tick() {
		
		// swap the buffers each tick
		{
			int tmp = ssboSrcId;
			ssboSrcId = ssboDstId;
			ssboDstId = tmp;
		}
		
		GL20.glUseProgram(computeProgram);
		
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, ssboSrcId);
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, ssboDstId);
		
		GL20.glUniform2i(GL20.glGetUniformLocation(computeProgram, "dimensions"), COLS, ROWS);
		
		GL43.glDispatchCompute(COLS / workGroupSizeX, ROWS / workGroupSizeY, 1);
		
		GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
		
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, 0);
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, 0);
		
		GL20.glUseProgram(0);
	}
	
	private void draw(int mouseX, int mouseY, int mode) {
		if (mouseX < cam.x || mouseX > cam.x + canvasSize.x || mouseY < cam.y || mouseY > cam.y + canvasSize.y) { return; }
		
		int cellX = (int)((mouseX - cam.x) / cellSize.x);
		int cellY = (int)((mouseY - cam.y) / cellSize.y);
		
		if ((mode == 0 || mode == 2) && cellX == lastDrawCell.x && cellY == lastDrawCell.y) { return; }
		if (mode == 1 && cellX == lastKillCell.x && cellY == lastKillCell.y) { return; }
		
		int off = (cellY * COLS + cellX) * 4;
		
		IntBuffer buffer = null;
		int cell = 0;
		
		if (mode == 0 || mode == 1) { buffer = BufferUtils.createIntBuffer(1); }
		
		if (mode == 0) {
			
			cell = 1;
			
		} else if (mode == 1) {
			
			cell = 0;
			
		} else if (mode == 2) {
		
			GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboDstId);
			
			ByteBuffer bytebuffer = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, off, 4, GL30.GL_MAP_READ_BIT, null);
			GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
			if (bytebuffer == null) { System.err.println("glMapBufferRange in mousePressed(..) failed."); return; }
			
			buffer = bytebuffer.asIntBuffer();
			cell = buffer.get();
			
			cell = (cell == 0) ? 1 : 0;
		}
		
		buffer.rewind();
		buffer.put(cell);
		buffer.flip();
		
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboDstId);
		GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, off, buffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
		
		if (mode == 0 || mode == 2) { lastDrawCell.set(cellX, cellY); } 
		else { lastKillCell.set(cellX, cellY); }
	}
	
	
	@Override
	public void render(GameContainer gc, Graphics g) throws SlickException {
		
		g.setColor(Color.gray);
		g.fillRect(0, 0, Display.getWidth(), Display.getHeight());
		
		cellShader.bind();
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, ssboDstId);
		
		cellShader.setUniform2f("cellSize", cellSize.x, cellSize.y);
		cellShader.setUniform2i("dimensions", COLS, ROWS);
		cellShader.setUniform2f("cam", cam.x, cam.y);
		
		g.fillRect(cam.x, cam.y, canvasSize.x, canvasSize.y);
		
		cellShader.unbind();
		
		if (showGrid) {
			
			g.setColor(Color.darkGray);
			g.beginQuad();
			
			for (int i = 0; i < (int)(canvasSize.x / cellSize.x)+1; ++i) {
				
				g.fillRectEmbedded(cam.x + i * cellSize.x, cam.y, 1, canvasSize.y);
			}
			for (int i = 0; i < (int)(canvasSize.y / cellSize.y)+1; ++i) {
				
				g.fillRectEmbedded(cam.x, cam.y + i * cellSize.y, canvasSize.x, 1);
			}
			
			g.endQuad();
		}
		
		g.setColor(Color.white);
		g.drawString("Tickrate: "+tickRate, 10, 24);
		
		if (paused) { g.drawString("Paused", 10, Display.getHeight() - 24); }
	}
	
	
	public void handleInput(int key, char c, boolean pressed) throws SlickException {
		
		if (pressed) {
			
			if (c == '+') {
				
				if (tickRate < 2048) { tickRate *= 2; }
				
			} else if (c == '-') {
				
				if (tickRate > 1) { tickRate /= 2; }
				
			} else if (key == Input.KEY_B) {
				
				if (benchmark) {
					
					game.setTargetFrameRate(savedFPS);
					tickRate = savedTickRate;
					
				} else {
					
					savedFPS = game.getTargetFrameRate();
					game.setTargetFrameRate(-1);
					savedTickRate = tickRate;
					tickRate = -1;
				}
				
				benchmark = !benchmark;
				
			} else if (key == Input.KEY_SPACE || key == Input.KEY_P) {
				
				paused = !paused;
				
			} else if (key == Input.KEY_G) {
				
				showGrid = !showGrid;
			}
		}
	}
	
	@Override
	public void mouseReleased(int button, int x, int y) {
		
		if (button == Input.MOUSE_LEFT_BUTTON) { lastDrawCell.set(-1, -1); }
		else if (button == Input.MOUSE_RIGHT_BUTTON) { lastKillCell.set(-1, -1); }
	}
	
	
	private int createShader(String filename, int shaderType) throws Exception {
		
		int shader = 0;
		
		try {
			
			shader = GL20.glCreateShader(shaderType);
			
			if(shader == 0)
				return 0;
			
			GL20.glShaderSource(shader, Files.readText(new File(filename)));
			GL20.glCompileShader(shader);
			
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
				throw new RuntimeException("Error creating shader: " + getLogInfo(shader));
			
			return shader;
			
		} catch(Exception exc) {
			
			GL20.glDeleteShader(shader);
			throw exc;
		}
	}
	
	private static String getLogInfo(int obj) {
		
		return ARBShaderObjects.glGetInfoLogARB(obj, ARBShaderObjects.glGetObjectParameteriARB(obj, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB));
	}
	
	
	@Override
	public void keyPressed(int key, char c) {
		try { handleInput(key, c, true); } catch (SlickException e) { e.printStackTrace(); }
	}
	@Override
	public void keyReleased(int key, char c) {
		try { handleInput(key, c, false); } catch (SlickException e) { e.printStackTrace(); }
	}
	
	private static void setAbspath() {
		
		try {
			
			abspath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath().replace("\\", "/");
			
			if (abspath.endsWith("/bin")) {
				
				abspath = abspath.substring(0, abspath.indexOf("/bin"));
			}
			
			if (abspath.endsWith(".jar")) {
				
				abspath = new File(abspath).getParentFile().getAbsolutePath();
			}
			
		} catch (Exception e) { e.printStackTrace(); }
	}
	
}
