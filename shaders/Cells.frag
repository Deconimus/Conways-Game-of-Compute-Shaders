#version 430


layout(origin_upper_left) in vec4 gl_FragCoord;

layout(std430, binding=4) buffer CellsBuffer
{
	int Cells[];
};


uniform vec2 cellSize;
uniform ivec2 dimensions;

uniform vec2 cam;

uniform vec4 cellColor = vec4(1.0, 1.0, 1.0, 1.0), bgColor = vec4(0.0, 0.0, 0.0, 1.0);


void main() {
	
	ivec2 pos = ivec2((gl_FragCoord.x - cam.x) / cellSize.x, (gl_FragCoord.y - cam.y) / cellSize.y);
	
	vec4 col = bgColor;
	
	if (Cells[pos.y * dimensions.x + pos.x] != 0) {
		
		col = cellColor;
	}
	
    gl_FragColor = col;
}