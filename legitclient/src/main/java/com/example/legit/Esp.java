package com.example.legit;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Рендер сквозь стены: белая заливка контейнеров + хитбокс невидимых игроков. */
public final class Esp {
	private static final int CHUNK_RADIUS = 6; // 6 чанков = ~96 блоков

	// Пайплайн без проверки глубины = рисуется поверх всего (сквозь стены)
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("legitclient", "pipeline/esp_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;

	private Esp() {}

	public static void register() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(Esp::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (!LegitClient.STORAGE.enabled && !LegitClient.INVIS.enabled) return;

		PoseStack ms = ctx.matrices();
		if (ms == null) return;

		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		ms.pushPose();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ms.last().pose();

		buffer = null;
		if (LegitClient.STORAGE.enabled) drawStorage(mc, m);
		if (LegitClient.INVIS.enabled) drawInvisible(mc, m, pt);

		ms.popPose();

		if (buffer != null) drawBuffer(mc);
	}

	// ---------- контейнеры: сундуки, шалкеры, бочки, воронки, печки и т.д. ----------
	private static void drawStorage(Minecraft mc, Matrix4f m) {
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		for (int cx = pcx - CHUNK_RADIUS; cx <= pcx + CHUNK_RADIUS; cx++) {
			for (int cz = pcz - CHUNK_RADIUS; cz <= pcz + CHUNK_RADIUS; cz++) {
				ChunkAccess access = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
				if (!(access instanceof LevelChunk chunk)) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					// removed = блок сломан/заменён -> белое сразу пропадает
					if (be.isRemoved() || !isStorage(be)) continue;
					BlockPos pos = be.getBlockPos();
					BlockState st = mc.level.getBlockState(pos);
					if (st.isAir() || st.getBlock() != be.getBlockState().getBlock()) continue;
					VoxelShape shape = st.getShape(mc.level, pos);
					if (shape.isEmpty()) continue;
					AABB b = shape.bounds().move(pos).inflate(0.002);
					fillBox(m, b, 1f, 1f, 1f, 0.6f);
				}
			}
		}
	}

	private static boolean isStorage(BlockEntity be) {
		return be instanceof BaseContainerBlockEntity   // сундук, бочка, шалкер, воронка, раздатчик, печки, зельеварка, крафтер
				|| be instanceof EnderChestBlockEntity
				|| be instanceof ChiseledBookShelfBlockEntity
				|| be instanceof DecoratedPotBlockEntity;
	}

	// ---------- невидимые игроки: настоящий хитбокс ----------
	private static void drawInvisible(Minecraft mc, Matrix4f m, float pt) {
		for (Player p : mc.level.players()) {
			if (p == mc.player) continue;
			if (!p.isInvisible() || p.isSpectator() || !p.isAlive()) continue;

			Vec3 shift = p.getPosition(pt).subtract(p.position());
			AABB b = p.getBoundingBox().move(shift);

			fillBox(m, b, 1f, 0.15f, 0.15f, 0.22f);   // полупрозрачная заливка
			edges(m, b, 1f, 0.2f, 0.2f, 1f);          // яркий каркас
		}
	}

	// ---------- примитивы ----------
	private static void edges(Matrix4fc m, AABB b, float r, float g, float bl, float a) {
		double t = 0.012;
		double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
		for (double y : ys) for (double z : zs)
			fillBox(m, new AABB(b.minX, y - t, z - t, b.maxX, y + t, z + t), r, g, bl, a);
		for (double x : xs) for (double z : zs)
			fillBox(m, new AABB(x - t, b.minY, z - t, x + t, b.maxY, z + t), r, g, bl, a);
		for (double x : xs) for (double y : ys)
			fillBox(m, new AABB(x - t, y - t, b.minZ, x + t, y + t, b.maxZ), r, g, bl, a);
	}

	private static void fillBox(Matrix4fc m, AABB b, float r, float g, float bl, float a) {
		if (buffer == null) {
			buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
		}
		float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
		float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
		// низ / верх
		q(m, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r,g,bl,a);
		q(m, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, r,g,bl,a);
		// север / юг
		q(m, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, r,g,bl,a);
		q(m, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, r,g,bl,a);
		// запад / восток
		q(m, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, r,g,bl,a);
		q(m, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, r,g,bl,a);
	}

	private static void q(Matrix4fc m,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float r, float g, float b, float a) {
		buffer.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		buffer.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		buffer.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
		buffer.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
	}

	// ---------- отправка накопленных вершин на GPU (схема из Fabric Docs для 1.21.11) ----------
	private static void drawBuffer(Minecraft mc) {
		MeshData built = buffer.buildOrThrow();
		buffer = null;
		MeshData.DrawState params = built.drawState();
		VertexFormat format = params.format();

		GpuBuffer vertices = upload(params, format, built);
		draw(mc, built, params, vertices, format);

		vertexBuffer.rotate();
	}

	private static GpuBuffer upload(MeshData.DrawState params, VertexFormat format, MeshData built) {
		int size = params.vertexCount() * format.getVertexSize();

		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> "legitclient esp", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(built.vertexBuffer(), view.data());
		}
		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft mc, MeshData built, MeshData.DrawState params, GpuBuffer vertices, VertexFormat format) {
		// режим у пайплайна QUADS -> сортируем и грузим индексный буфер
		built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
		GpuBuffer indices = THROUGH_WALLS.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
		VertexFormat.IndexType indexType = built.drawState().indexType();

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "legitclient esp pass",
				mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(THROUGH_WALLS);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, params.indexCount(), 1);
		}
		built.close();
	}

	public static void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
