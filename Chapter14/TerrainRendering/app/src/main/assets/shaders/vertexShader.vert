#version 300 es

uniform mat4 u_mvpMatrix;
uniform vec3 u_lightDirection;
uniform sampler2D s_texture;

layout(location = 0) in vec4 a_position;

out vec4 v_color;

void main()
{
  // Compute vertex normal from height map
  float hxl = textureOffset( s_texture, a_position.xy, ivec2(-1,  0) ).r;
  float hxr = textureOffset( s_texture, a_position.xy, ivec2( 1,  0) ).r;
  float hyl = textureOffset( s_texture, a_position.xy, ivec2( 0, -1) ).r;
  float hyr = textureOffset( s_texture, a_position.xy, ivec2( 0,  1) ).r;
  vec3 u = normalize( vec3(0.05, 0.0, hxr-hxl) );
  vec3 v = normalize( vec3(0.0, 0.05, hyr-hyl) );
  vec3 normal = cross( u, v );

  // Compute diffuse lighting
  float diffuse = dot( normal, u_lightDirection );
  v_color = vec4( vec3(diffuse), 1.0 );

  // Get vertex position from height map
  float h = texture ( s_texture, a_position.xy ).r;
  vec4 v_position = vec4 ( a_position.xy, h/2.5, a_position.w );
  gl_Position = u_mvpMatrix * v_position;
}