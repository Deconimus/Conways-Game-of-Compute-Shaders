#version 430 compatibility
#extension GL_ARB_compute_shader : enable
#extension GL_ARB_shader_storage_buffer_object : enable


layout(std430, binding=4) buffer SrcCells
{
	int Src[];
};

layout(std430, binding=5) buffer DstCells
{
	int Dst[];
};


layout (local_size_x = 16, local_size_y = 16, local_size_z = 1) in;


uniform ivec2 dimensions;


void main()
{
	uvec2 gid = gl_GlobalInvocationID.xy;
	
	bool alive = Src[gid.y * dimensions.x + gid.x] != 0;
	int val = 0;
	
	uint aliveNeighbors = 0;
	
	/*
	int i, j, x, y;
	for (i = -1; i < 2; ++i)
	{
		x = int(gid.x) + i;
		if (x < 0 || x >= dimensions.x) { continue; }
		
		for (j = -1; j < 2; ++j)
		{
			y = int(gid.y) + j;
			if (y < 0 || y >= dimensions.y || (i == 0 && j == 0)) { continue; }
			
			aliveNeighbors += Src[y * dimensions.x + x];
		}
	}
	*/
	
	// seems like out of bound-reads will return 0
	
	int x = int(gid.x), y = int(gid.y);
	
	aliveNeighbors += Src[(y-1) * dimensions.x + x-1];
	aliveNeighbors += Src[(y-1) * dimensions.x + x];
	aliveNeighbors += Src[(y-1) * dimensions.x + x+1];
	aliveNeighbors += Src[y * dimensions.x + x-1];
	aliveNeighbors += Src[y * dimensions.x + x+1];
	aliveNeighbors += Src[(y+1) * dimensions.x + x-1];
	aliveNeighbors += Src[(y+1) * dimensions.x + x];
	aliveNeighbors += Src[(y+1) * dimensions.x + x+1];
	
	if (aliveNeighbors == 3 || (alive && aliveNeighbors == 2)) { val = 1; }
	
	Dst[gid.y * dimensions.x + gid.x] = val;
}